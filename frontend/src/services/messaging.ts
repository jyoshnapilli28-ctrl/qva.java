import { request } from './http'
import { mutate } from './operations'

export type Conversation = { id: string; peerId: number; peerName: string; createdAt: string }
export type Group = { id: string; name: string; ownerId: number; createdAt: string }
export type Participant = { id: number; name: string }
export type Envelope = { sequence: number; senderUserId: number; envelope: string; acceptedAt: string }
export type Receipt = { sequence: number; recipientUserId: number; deliveredAt: string; readAt: string | null }

const path = '/api/v2/conversations'
export const relayStatus = (signal: AbortSignal) => request<{ enabled: boolean }>(path + '/status', { signal })
export const conversations = (signal: AbortSignal) => request<Conversation[]>(path, { signal })
export const groups = (signal: AbortSignal) => request<Group[]>(path + '/groups', { signal })
export const createConversation = (peerId: number) => mutate<Conversation>('/conversations/direct', { peerId })
export const createGroup = (name: string) => mutate<Group>('/conversations/groups', { name })
export const participants = (id: string, signal: AbortSignal) =>
  request<Participant[]>(`${path}/groups/${id}/participants`, { signal })
export const addParticipant = (id: string, userId: number) =>
  mutate(`/conversations/groups/${id}/participants`, { userId })
export async function removeParticipant(id: string, userId: number) {
  const csrf = await token()
  return request(`${path}/groups/${id}/participants/${userId}`, {
    method: 'DELETE', headers: { [csrf.headerName]: csrf.token },
  })
}
const token = () => request<{ headerName: string; token: string }>('/api/v2/csrf')
export const history = (id: string, query: string, signal?: AbortSignal) =>
  request<Envelope[]>(`${path}/${id}/envelopes${query}`, signal ? { signal } : {})
export const receipts = (id: string, sequence: number, signal?: AbortSignal) =>
  request<Receipt[]>(`${path}/${id}/envelopes/${sequence}/receipt`, signal ? { signal } : {})
export const acknowledge = (id: string, sequence: number, state: 'READ' | 'DELIVERED') =>
  mutate<Receipt>(`/conversations/${id}/envelopes/${sequence}/receipt`, { state })
export async function sendFixture(id: string, key: string, body: Uint8Array<ArrayBuffer>) {
  const csrf = await token()
  return request<Envelope>(`${path}/${id}/envelopes`, {
    method: 'POST', body, headers: { [csrf.headerName]: csrf.token,
      'Content-Type': 'application/octet-stream', 'Idempotency-Key': key, 'QVA-Envelope-Version': '1' },
  })
}
