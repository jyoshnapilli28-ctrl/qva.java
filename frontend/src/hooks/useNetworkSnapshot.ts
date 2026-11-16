import { useCallback, useEffect, useState } from 'react'
import { isUnauthenticated } from '../services/http'
import { getHealth, getMembers } from '../services/network'
import type { Health, Member, Resource } from '../types'

export const SNAPSHOT_INTERVAL_MS = 15_000

function toResource<T>(result: PromiseSettledResult<T>): Resource<T> {
  return result.status === 'fulfilled' ? { status: 'ready', data: result.value } : { status: 'error' }
}

export function useNetworkSnapshot(onExpired: () => void) {
  const [health, setHealth] = useState<Resource<Health>>({ status: 'loading' })
  const [members, setMembers] = useState<Resource<Member[]>>({ status: 'loading' })
  const [updated, setUpdated] = useState<Date>()
  const [refreshing, setRefreshing] = useState(true)
  const [revision, setRevision] = useState(0)
  const refresh = useCallback(() => setRevision(value => value + 1), [])

  useEffect(() => {
    const lifetime = new AbortController()
    let inFlight = false

    async function load() {
      if (inFlight || lifetime.signal.aborted) return
      inFlight = true
      setRefreshing(true)
      try {
        const signal = AbortSignal.any([lifetime.signal, AbortSignal.timeout(8000)])
        const [service, roster] = await Promise.allSettled([getHealth(signal), getMembers(signal)])
        if (lifetime.signal.aborted) return
        if ([service, roster].some(result => result.status === 'rejected' && isUnauthenticated(result.reason))) {
          onExpired()
          return
        }
        setHealth(toResource(service))
        setMembers(toResource(roster))
        setUpdated(service.status === 'fulfilled' || roster.status === 'fulfilled' ? new Date() : undefined)
      } finally {
        inFlight = false
        if (!lifetime.signal.aborted) setRefreshing(false)
      }
    }

    void load()
    const poll = window.setInterval(load, SNAPSHOT_INTERVAL_MS)
    return () => { lifetime.abort(); window.clearInterval(poll) }
  }, [onExpired, revision])

  return { health, members, updated, refreshing, refresh }
}

export type NetworkSnapshot = ReturnType<typeof useNetworkSnapshot>
