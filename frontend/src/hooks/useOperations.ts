import { useCallback, useEffect, useState } from 'react'
import { getHostDashboard, getIdentity, getTopology, liveUrl, type HostDashboard, type Identity, type Topology } from '../services/operations'
import { isUnauthenticated } from '../services/http'
import type { Resource } from '../types'

function toResource<T>(result: PromiseSettledResult<T>): Resource<T> {
  return result.status === 'fulfilled' ? { status: 'ready', data: result.value } : { status: 'error' }
}

export function useOperations(onExpired: () => void) {
  const [identity, setIdentity] = useState<Resource<Identity>>({ status: 'loading' })
  const [topology, setTopology] = useState<Resource<Topology>>({ status: 'loading' })
  const [dashboard, setDashboard] = useState<Resource<HostDashboard>>({ status: 'loading' })
  const [live, setLive] = useState(false)
  const [revision, setRevision] = useState(0)
  const refresh = useCallback(() => setRevision(value => value + 1), [])

  useEffect(() => {
    const lifetime = new AbortController()
    let inFlight = false
    async function load() {
      if (lifetime.signal.aborted || inFlight) return
      inFlight = true
      const signal = AbortSignal.any([lifetime.signal, AbortSignal.timeout(8000)])
      try {
        const account = await getIdentity(signal)
        if (lifetime.signal.aborted) return
        setIdentity({ status: 'ready', data: account })
        if (account.device?.status === 'REVOKED') {
          setTopology({ status: 'error' })
          setDashboard({ status: 'error' })
          return
        }
        const canManage = account.role === 'HOST' && account.device?.status === 'APPROVED'
        const [network, host] = await Promise.allSettled([
          getTopology(signal), canManage ? getHostDashboard(signal) : Promise.resolve(null),
        ] as const)
        if (lifetime.signal.aborted) return
        if ([network, host].some(result => result.status === 'rejected' && isUnauthenticated(result.reason))) {
          onExpired()
          return
        }
        setTopology(toResource(network))
        setDashboard(host.status === 'fulfilled' && host.value
          ? { status: 'ready', data: host.value } : { status: 'error' })
      } catch (error) {
        if (lifetime.signal.aborted) return
        if (isUnauthenticated(error)) onExpired()
        else {
          setIdentity({ status: 'error' })
          setTopology({ status: 'error' })
          setDashboard({ status: 'error' })
        }
      } finally {
        inFlight = false
      }
    }
    void load()
    const poll = window.setInterval(load, 15_000)
    return () => { lifetime.abort(); window.clearInterval(poll) }
  }, [revision, onExpired])

  const deviceId = identity.status === 'ready' && identity.data.device?.status === 'APPROVED'
    ? identity.data.device.id : null

  useEffect(() => {
    setLive(false)
    if (!deviceId) return
    let stopped = false
    let backoff = 1000
    let socket: WebSocket | undefined
    let heartbeat: number | undefined
    let retry: number | undefined
    let reconciliation: number | undefined
    const seen = new Set<string>()

    function connect() {
      socket = new WebSocket(liveUrl())
      socket.onopen = () => {
        if (stopped) return
        setLive(true)
        backoff = 1000
        refresh()
        heartbeat = window.setInterval(() => {
          if (socket?.readyState === WebSocket.OPEN) socket.send('heartbeat')
        }, 10_000)
      }
      socket.onmessage = message => {
        if (stopped || typeof message.data !== 'string') return
        try {
          const event = JSON.parse(message.data) as { id?: unknown }
          if (typeof event.id !== 'string' || seen.has(event.id)) return
          seen.add(event.id)
          if (seen.size > 256) {
            const oldest = seen.values().next().value
            if (oldest) seen.delete(oldest)
          }
          if (reconciliation === undefined) {
            reconciliation = window.setTimeout(() => {
              reconciliation = undefined
              refresh()
            }, 250)
          }
        } catch {
          // Ignore any frame outside the server event contract.
        }
      }
      socket.onclose = () => {
        window.clearInterval(heartbeat)
        if (stopped) return
        setLive(false)
        refresh()
        retry = window.setTimeout(connect, backoff)
        backoff = Math.min(backoff * 2, 15_000)
      }
    }

    connect()
    return () => {
      stopped = true
      window.clearInterval(heartbeat)
      window.clearTimeout(retry)
      window.clearTimeout(reconciliation)
      socket?.close()
    }
  }, [deviceId, refresh])

  return { identity, topology, dashboard, live, refresh }
}

export type Operations = ReturnType<typeof useOperations>
