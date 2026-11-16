import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { MessagesPage } from '../src/pages/MessagesPage'
import type { NetworkSnapshot } from '../src/hooks/useNetworkSnapshot'
import type { Operations } from '../src/hooks/useOperations'

afterEach(() => { cleanup(); vi.unstubAllGlobals() })

const operations: Operations = {
  identity: { status: 'ready', data: {
    username: 'fixture_a', role: 'MEMBER', session: null, mayBootstrapHost: false,
    device: { id: 'fixture', userId: 1, username: 'fixture_a', name: 'Fixture browser',
      status: 'APPROVED', registeredAt: '', lastSeen: null, approvedAt: null, revokedAt: null },
  } },
  topology: { status: 'loading' }, dashboard: { status: 'loading' }, live: false, refresh: vi.fn(),
}
const snapshot: NetworkSnapshot = {
  health: { status: 'loading' },
  members: { status: 'ready', data: [{ id: 2, username: 'fixture_b', displayName: 'Member B', online: false }] },
  updated: undefined, refreshing: false, refresh: vi.fn(),
}
const json = (value: unknown) => new Response(JSON.stringify(value), {
  headers: { 'content-type': 'application/json' },
})

it('explains a disabled relay and offers no submission controls', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => json({ enabled: false })))
  render(<MessagesPage operations={operations} snapshot={snapshot} />)
  expect(await screen.findByText(/development relay is disabled/)).toBeTruthy()
  expect(screen.queryByRole('button', { name: 'Open conversation' })).toBeNull()
})

it('creates a conversation through the authenticated API and refreshes the member list', async () => {
  let created = false
  const entry = { id: 'direct-fixture', peerId: 2, peerName: 'Member B', createdAt: '2026-01-01T00:00:00Z' }
  const fetch = vi.fn(async (route: string, init?: RequestInit) => {
    if (route.endsWith('/status')) return json({ enabled: true })
    if (route.endsWith('/groups')) return json([])
    if (route.endsWith('/csrf')) return json({ headerName: 'X-CSRF-TOKEN', token: 'test-only' })
    if (route.endsWith('/direct')) {
      expect(init?.headers).toMatchObject({ 'X-CSRF-TOKEN': 'test-only' })
      expect(init?.body).toBe('{"peerId":2}')
      created = true
      return json(entry)
    }
    return json(created ? [entry] : [])
  })
  vi.stubGlobal('fetch', fetch)
  render(<MessagesPage operations={operations} snapshot={snapshot} />)
  await userEvent.selectOptions(await screen.findByLabelText('Participant'), '2')
  await userEvent.click(screen.getByRole('button', { name: 'Open conversation' }))
  expect(await screen.findByRole('button', { name: 'Member B' })).toBeTruthy()
  expect(screen.queryByLabelText('Message body')).toBeNull()
})
