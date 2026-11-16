import { useState, type FormEvent } from 'react'
import { ShieldCheck } from 'lucide-react'
import { Button, Notice, Panel } from '../ui/Primitives'
import { bootstrapHost, formatTimestamp, registerDevice } from '../../services/operations'
import type { Operations } from '../../hooks/useOperations'

export function IdentityPanel({ operations }: { operations: Operations }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const { identity, refresh, live } = operations

  async function execute(action: () => Promise<unknown>) {
    setBusy(true)
    setError('')
    try {
      await action()
      refresh()
    } catch {
      setError('The device change could not be completed. Refresh and try again.')
      refresh()
    } finally {
      setBusy(false)
    }
  }

  function register(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const name = String(new FormData(event.currentTarget).get('name')).trim()
    if (name) void execute(() => registerDevice(name))
  }

  return <Panel icon={<ShieldCheck size={17} />} title="Your account and device" detail="Workspace identity">
    {identity.status === 'loading' && <Notice kind="loading">Loading workspace identity…</Notice>}
    {identity.status === 'error' && <Notice kind="error" onRetry={refresh}>Workspace identity is unavailable.</Notice>}
    {identity.status === 'ready' && <>
      <dl className="detail-list">
        <div><dt>Account</dt><dd>{identity.data.username}</dd></div>
        <div><dt>Role</dt><dd>{identity.data.role === 'HOST' ? 'Host' : 'Member'}</dd></div>
        <div><dt>Current device</dt><dd>{identity.data.device?.name ?? 'Unregistered'}</dd></div>
        <div><dt>Device approval</dt><dd>{identity.data.device?.status ?? 'Unavailable'}</dd></div>
        <div><dt>Application connection</dt><dd>{live ? 'Connected' : 'Disconnected'}</dd></div>
        <div><dt>Session observed</dt><dd>{formatTimestamp(identity.data.session?.startedAt)}</dd></div>
        <div><dt>Last seen</dt><dd>{formatTimestamp(identity.data.device?.lastSeen)}</dd></div>
      </dl>
      {identity.data.device?.status === 'REVOKED' && <Notice kind="error">This browser’s device registration was revoked.</Notice>}
      {!identity.data.device && <form className="device-form" onSubmit={register}>
        <label>Device name<input name="name" maxLength={60} required placeholder="e.g. My laptop" /></label>
        <Button type="submit" disabled={busy}>Register this browser</Button>
      </form>}
      {identity.data.device?.status === 'PENDING' && !identity.data.mayBootstrapHost &&
        <Notice kind="info">Waiting for a host to approve this device.</Notice>}
      {identity.data.mayBootstrapHost &&
        <Button disabled={busy} onClick={() => execute(bootstrapHost)}>Approve first host device</Button>}
      {error && <Notice kind="error">{error}</Notice>}
    </>}
  </Panel>
}
