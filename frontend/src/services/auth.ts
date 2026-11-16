import type { Session } from '../types'
import { request } from './http'

export const getSession = (signal?: AbortSignal) => request<Session>('/api/auth/me', { signal })

export async function signIn(username: string, password: string) {
  await request('/api/auth/login', {
    method: 'POST',
    body: new URLSearchParams({ username, password }),
  })
  return getSession()
}

export function signOut() {
  return request('/api/auth/logout', { method: 'POST' })
}
