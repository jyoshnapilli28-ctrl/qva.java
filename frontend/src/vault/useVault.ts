import { useCallback, useEffect, useRef, useState } from 'react'
import { validateRecord, VaultError, type VaultIssue } from './format'
import { idleMilliseconds, onWorkspaceVaultLock } from './lifecycle'
import { readVault } from './storage'
import type { VaultRequest, VaultResponse } from './vault.worker'

type Status = 'checking' | 'not-initialized' | 'locked' | 'unlocking' | 'unlocked' | 'error' | 'recovery-required'

function supported() {
  return window.isSecureContext && !!globalThis.crypto?.subtle && !!window.indexedDB
    && !!navigator.locks && typeof BroadcastChannel === 'function' && typeof Worker === 'function'
}

function runWorker(worker: Worker, data: VaultRequest, signal: AbortSignal) {
  return new Promise<VaultResponse>((resolve, reject) => {
    signal.addEventListener('abort', () => reject(signal.reason), { once: true })
    worker.onmessage = ({ data }: MessageEvent<VaultResponse>) => resolve(data)
    worker.onerror = () => reject(new VaultError('unavailable'))
    worker.onmessageerror = () => reject(new VaultError('unavailable'))
    worker.postMessage(data, [data.password.buffer])
  })
}

export function useVault(deviceId: string) {
  const [status, setStatus] = useState<Status>('checking')
  const [message, setMessage] = useState('')
  const operation = useRef<AbortController | null>(null)
  const revision = useRef(0)

  const inspect = useCallback(async () => {
    const current = ++revision.current
    try {
      if (!supported()) throw new VaultError('unavailable')
      const record = await readVault(deviceId)
      if (current !== revision.current) return
      if (record !== undefined) validateRecord(record, deviceId)
      setStatus(record === undefined ? 'not-initialized' : 'locked')
    } catch (failure) {
      if (current !== revision.current) return
      if (failure instanceof VaultError && failure.issue === 'malformed') {
        setStatus('recovery-required')
      } else {
        setStatus('error')
      }
    }
  }, [deviceId])

  const lock = useCallback(() => {
    operation.current?.abort()
    operation.current = null
    setMessage('')
    setStatus('checking')
    void inspect()
  }, [inspect])

  useEffect(() => {
    void inspect()
    const unsubscribe = onWorkspaceVaultLock(lock)
    function leaving() {
      operation.current?.abort()
      operation.current = null
      revision.current++
      setStatus('checking')
    }
    window.addEventListener('pagehide', leaving)
    window.addEventListener('pageshow', lock)
    return () => {
      leaving()
      unsubscribe()
      window.removeEventListener('pagehide', leaving)
      window.removeEventListener('pageshow', lock)
    }
  }, [inspect, lock])

  useEffect(() => {
    if (status !== 'unlocked' && status !== 'unlocking') return
    let deadline = Date.now() + idleMilliseconds
    let timer = window.setTimeout(lock, idleMilliseconds)
    function activity() {
      if (Date.now() >= deadline) {
        lock()
        return
      }
      deadline = Date.now() + idleMilliseconds
      window.clearTimeout(timer)
      timer = window.setTimeout(lock, idleMilliseconds)
    }
    function visible() { if (Date.now() >= deadline) lock() }
    window.addEventListener('pointerdown', activity)
    window.addEventListener('keydown', activity)
    document.addEventListener('visibilitychange', visible)
    return () => {
      window.clearTimeout(timer)
      window.removeEventListener('pointerdown', activity)
      window.removeEventListener('keydown', activity)
      document.removeEventListener('visibilitychange', visible)
    }
  }, [lock, status])

  async function submit(intent: VaultRequest['intent'], password: Uint8Array) {
    if (operation.current) {
      password.fill(0)
      return
    }
    const attempt = new AbortController()
    const { signal } = attempt
    operation.current = attempt
    revision.current++
    setMessage('')
    setStatus('unlocking')
    try {
      await navigator.locks.request('qva-vault:' + deviceId, { ifAvailable: true }, async lease => {
        if (signal.aborted) return
        if (!lease) {
          setMessage('This vault is active in another tab. Lock it there before continuing.')
          setStatus(intent === 'setup' ? 'not-initialized' : 'locked')
          return
        }
        const worker = new Worker(new URL('./vault.worker.ts', import.meta.url), { type: 'module' })
        try {
          const result = await runWorker(worker, { deviceId, intent, password }, signal)
          if (signal.aborted) return
          if (!result.unlocked) throw new VaultError(result.issue)
          setStatus('unlocked')
          await new Promise<void>(resolve => signal.addEventListener('abort', () => resolve(), { once: true }))
        } finally {
          worker.terminate()
        }
      })
    } catch (failure) {
      if (signal.aborted) return
      const issue: VaultIssue = failure instanceof VaultError ? failure.issue : 'unavailable'
      if (issue === 'credentials') {
        setMessage('Unable to unlock. Check your encryption password. The stored vault may also be damaged.')
        setStatus(intent === 'setup' ? 'not-initialized' : 'locked')
      } else if (issue === 'malformed' || issue === 'missing') {
        setStatus('recovery-required')
      } else if (issue === 'exists') {
        await inspect()
      } else {
        setStatus('error')
      }
    } finally {
      if (password.byteLength) password.fill(0)
      if (operation.current === attempt) operation.current = null
    }
  }

  return { status, message, submit, lock }
}
