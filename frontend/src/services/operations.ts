import { request } from './http'

export type Device = {
  id: string
  userId: number
  username: string
  name: string
  status: 'APPROVED' | 'PENDING' | 'REVOKED'
  registeredAt: string
  lastSeen: string | null
  approvedAt: string | null
  revokedAt: string | null
}
export type AppSession = {
  id: string
  userId: number
  username: string
  deviceId: string | null
  startedAt: string
}
export type Identity = {
  username: string
  role: 'HOST' | 'MEMBER'
  device: Device | null
  session: AppSession | null
  mayBootstrapHost: boolean
}
export type DeviceState = {
  device: Device
  connected: boolean
  authenticated: boolean
  connectedAt: string | null
}
export type OperationEvent = {
  id: string
  timestamp: string
  type: string
  userId: number | null
  deviceId: string | null
  source: 'APPLICATION'
}
export type HostDashboard = {
  users: { id: number; username: string; displayName: string }[]
  devices: DeviceState[]
  sessions: AppSession[]
  activity: OperationEvent[]
}
export type Topology = { devices: DeviceState[]; vpnStatus: 'UNKNOWN' }

const api = '/api/v2'
export const getIdentity = (signal: AbortSignal) => request<Identity>(api + '/identity', { signal })
export const getTopology = (signal: AbortSignal) => request<Topology>(api + '/network', { signal })
export const getHostDashboard = (signal: AbortSignal) => request<HostDashboard>(api + '/host/dashboard', { signal })

export async function mutate<T>(path: string, body?: object, method: 'POST' | 'DELETE' = 'POST') {
  const csrf = await request<{ headerName: string; token: string }>(api + '/csrf')
  return request<T>(api + path, {
    method,
    headers: { [csrf.headerName]: csrf.token, 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  })
}

export const registerDevice = (name: string) => mutate<Device>('/devices', { name })
export const bootstrapHost = () => mutate<Device>('/devices/bootstrap-host')
export const updateDevice = (id: string, action: 'approve' | 'revoke') =>
  mutate<Device>(`/host/devices/${encodeURIComponent(id)}/${action}`)
export const terminateSession = (id: string) => mutate<{ terminated: boolean }>(
  `/host/sessions/${encodeURIComponent(id)}/terminate`,
)

export function liveUrl() {
  const url = new URL(api + '/live', location.origin)
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  return url.toString()
}

export function formatTimestamp(timestamp?: string | null) {
  return timestamp ? new Date(timestamp).toLocaleString() : 'Unavailable'
}
