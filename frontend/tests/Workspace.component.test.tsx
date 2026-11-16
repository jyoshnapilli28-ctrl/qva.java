import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MemberRoster } from '../src/components/network/MemberRoster'
import { TopologyCanvas } from '../src/components/network/TopologyCanvas'
import { LoginPage } from '../src/pages/LoginPage'
import { topologyFromHealth } from '../src/services/network'

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

describe('member presence', () => {
  it('renders loading, empty and API error as separate states', async () => {
    const retry = vi.fn()
    const { rerender } = render(<MemberRoster members={{ status: 'loading' }} onRetry={retry} />)
    expect(screen.getByRole('status').textContent).toContain('Loading')
    rerender(<MemberRoster members={{ status: 'ready', data: [] }} onRetry={retry} />)
    expect(screen.getByRole('status').textContent).toContain('No active accounts')
    rerender(<MemberRoster members={{ status: 'error' }} onRetry={retry} />)
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(retry).toHaveBeenCalledOnce()
    expect(screen.getByRole('alert').textContent).toContain('unavailable')
  })
})

it('shows an observed relay without using roster data as client topology', () => {
  render(<TopologyCanvas topology={topologyFromHealth({
    status: 'ready', data: { status: 'UP', version: '1.0.0', uptimeSeconds: 5 },
  })} />)
  expect(screen.getByText('Service reachable')).toBeTruthy()
  expect(screen.queryByRole('list', { name: 'Client connections' })).toBeNull()
  expect(screen.getByText(/Client topology is unavailable/)).toBeTruthy()
})

it('announces an expired session and moves focus to sign in', () => {
  vi.stubGlobal('__QVA_CLASSIC_URL__', '/')
  render(<MemoryRouter><LoginPage expired onLogin={vi.fn()} /></MemoryRouter>)
  expect(screen.getByRole('status').textContent).toContain('Your session expired')
  expect(document.activeElement).toBe(screen.getByRole('heading', { name: 'Sign in to QVA' }))
})
