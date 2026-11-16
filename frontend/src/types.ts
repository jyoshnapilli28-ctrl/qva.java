export type Session = { username: string; authenticated: true }
export type Health = { status: string; version: string; uptimeSeconds: number }
export type Member = { id: number; username: string; displayName: string; online: boolean }

export type Resource<T> =
  | { status: 'loading' }
  | { status: 'ready'; data: T }
  | { status: 'error' }

export type NetworkNode = {
  id: string
  label: string
  kind: 'host' | 'client'
  status: 'reachable' | 'unavailable' | 'unknown'
}

export type NetworkTopology = {
  host?: NetworkNode
  clients: NetworkNode[]
}
