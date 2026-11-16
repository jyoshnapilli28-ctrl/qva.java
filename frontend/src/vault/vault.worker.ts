import { createVault, unlockVault, VaultError, type VaultIssue } from './format'
import { addVault, readVault } from './storage'

export type VaultRequest = { deviceId: string; intent: 'setup' | 'unlock'; password: Uint8Array }
export type VaultResponse = { unlocked: true } | { unlocked: false; issue: VaultIssue }

// Retain only a non-extractable key in the dedicated worker after unlock.
let identity: CryptoKey | undefined
let started = false

self.onmessage = async ({ data }: MessageEvent<VaultRequest>) => {
  if (started) return
  started = true
  const { intent, deviceId, password } = data
  try {
    const existing = await readVault(deviceId)
    if (intent === 'setup') {
      if (existing !== undefined) throw new VaultError('exists')
      const record = await createVault(deviceId, password.slice())
      identity = await unlockVault(record, deviceId, password)
      await addVault(record)
    } else {
      if (existing === undefined) throw new VaultError('missing')
      identity = await unlockVault(existing, deviceId, password)
    }
    if (identity.type !== 'private') throw new VaultError('malformed')
    self.postMessage({ unlocked: true } satisfies VaultResponse)
  } catch (error) {
    identity = undefined
    self.postMessage({
      unlocked: false, issue: error instanceof VaultError ? error.issue : 'unavailable',
    } satisfies VaultResponse)
  } finally {
    password.fill(0)
  }
}
