import { request } from './http'
import { mutate } from './operations'

export type Invitation = {
  id: string
  status: 'PENDING' | 'EXPIRED' | 'REVOKED' | 'REDEEMED'
  createdAt: string
  expiresAt: string
  revokedAt: string | null
  redeemedAt: string | null
  redeemedUsername: string | null
}
export type InvitationAudit = {
  id: string
  invitationId: string
  action: 'CREATED' | 'REVOKED' | 'REDEEMED'
  actorUsername: string | null
  affectedUsername: string | null
  occurredAt: string
}
export type CreatedInvitation = { id: string; token: string; createdAt: string; expiresAt: string }
export type Registration = { token: string; username: string; displayName: string; password: string }

export const getInvitations = (signal?: AbortSignal) => request<Invitation[]>('/api/v2/host/invitations', { signal })
export const getInvitationAudit = (signal?: AbortSignal) => request<InvitationAudit[]>('/api/v2/host/invitations/audit', { signal })
export const createInvitation = (expirationHours: number) =>
  mutate<CreatedInvitation>('/host/invitations', { expirationHours })
export const revokeInvitation = (id: string) => mutate<{ revoked: boolean }>(`/host/invitations/${encodeURIComponent(id)}`, undefined, 'DELETE')

export async function registerAccount(body: Registration) {
  const csrf = await request<{ headerName: string; token: string }>('/api/auth/csrf')
  return request<{ userId: number; username: string; displayName: string }>('/api/auth/register', {
    method: 'POST',
    headers: { [csrf.headerName]: csrf.token, 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}
