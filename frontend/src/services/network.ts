import type { Health, Member, NetworkTopology, Resource } from '../types'
import { request } from './http'

export const getHealth = (signal: AbortSignal) => request<Health>('/api/health', { signal })
export const getMembers = (signal: AbortSignal) => request<Member[]>('/api/users', { signal })

export function topologyFromHealth(health: Resource<Health>): NetworkTopology {
  return {
    host: health.status === 'ready' ? {
      id: 'relay',
      label: 'QVA relay',
      kind: 'host',
      status: health.data.status === 'UP' ? 'reachable' : 'unavailable',
    } : undefined,
    clients: [],
  }
}
