import { useState } from 'react'
import { Activity, Server, ShieldCheck, Users } from 'lucide-react'
import { PageHeader } from '../components/PageHeader'
import { InvitationManager } from '../components/operations/InvitationManager'
import { Button, Notice, Panel } from '../components/ui/Primitives'
import { formatTimestamp, terminateSession, updateDevice } from '../services/operations'
import type { Operations } from '../hooks/useOperations'
import type { NetworkSnapshot } from '../hooks/useNetworkSnapshot'

export function HostPage({ snapshot, operations }: { snapshot: NetworkSnapshot; operations: Operations }) {
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState(false)
  const [confirmation, setConfirmation] = useState<{ label: string; action: () => Promise<unknown> }>()
  const { identity, dashboard, refresh } = operations

  async function execute(action: () => Promise<unknown>) {
    setBusy(true)
    setFailure(false)
    try { await action() }
    catch { setFailure(true) }
    finally { setBusy(false); setConfirmation(undefined); refresh() }
  }

  if (identity.status === 'loading') return <Notice kind="loading">Checking host access…</Notice>
  if (identity.status === 'error') return <Notice kind="error" onRetry={refresh}>Host access could not be verified.</Notice>
  if (identity.data.role !== 'HOST' || identity.data.device?.status !== 'APPROVED') {
    return <><h1>Host access required</h1><Notice kind="info">This page requires a host account and an approved device.</Notice></>
  }

  const { device: ownDevice, session, username } = identity.data
  return <>
    <PageHeader title="Host dashboard" detail={`Workspace operations for ${username}`} onRefresh={refresh} />
    {dashboard.status === 'loading' && <Notice kind="loading">Loading host dashboard…</Notice>}
    {dashboard.status === 'error' && <Notice kind="error" onRetry={refresh}>Host dashboard is unavailable.</Notice>}
    {failure && <Notice kind="error">The requested action could not be completed. Refresh and try again.</Notice>}
    <div className="panel-grid operations-grid"><InvitationManager /></div>
    {confirmation && <Notice kind="info">
      {confirmation.label}
      <span className="operation-actions"><Button disabled={busy} onClick={() => execute(confirmation.action)}>Confirm</Button>
        <Button disabled={busy} onClick={() => setConfirmation(undefined)}>Cancel</Button></span>
    </Notice>}
    {dashboard.status === 'ready' && <div className="panel-grid operations-grid">
      <Panel icon={<Server size={17} />} title="Service and access" detail="Current server observations">
        <dl className="detail-list">
          <div><dt>Relay</dt><dd>{snapshot.health.status === 'ready' ? snapshot.health.data.status : 'Unavailable'}</dd></div>
          <div><dt>Registered accounts</dt><dd>{dashboard.data.users.length}</dd></div>
          <div><dt>Approved devices</dt><dd>{dashboard.data.devices.filter(entry => entry.device.status === 'APPROVED').length}</dd></div>
          <div><dt>Pending approvals</dt><dd>{dashboard.data.devices.filter(entry => entry.device.status === 'PENDING').length}</dd></div>
          <div><dt>Workspace sessions</dt><dd>{dashboard.data.sessions.length}</dd></div>
        </dl>
      </Panel>
      <Panel icon={<Users size={17} />} title="Accounts" detail="Active registered users">
        <ul className="operation-list">{dashboard.data.users.map(user => <li key={user.id}>{user.displayName} <span>@{user.username}</span></li>)}</ul>
      </Panel>
      <Panel icon={<ShieldCheck size={17} />} title="Device management" detail="Select a device to inspect it">
        {dashboard.data.devices.length === 0 && <Notice kind="empty">No devices have registered.</Notice>}
        {dashboard.data.devices.map(({ device, connected, authenticated }) => <details className="operation-item" key={device.id}>
          <summary>{device.name} <span>{device.status} · {connected ? 'Connected' : 'Disconnected'}</span></summary>
          <dl className="detail-list">
            <div><dt>Account</dt><dd>{device.username}</dd></div>
            <div><dt>Authentication</dt><dd>{authenticated ? 'Authenticated' : 'No active workspace session'}</dd></div>
            <div><dt>Last seen</dt><dd>{formatTimestamp(device.lastSeen)}</dd></div>
            <div><dt>Registered</dt><dd>{formatTimestamp(device.registeredAt)}</dd></div>
          </dl>
          <div className="operation-actions">
            {device.status === 'PENDING' && <Button aria-label={`Approve ${device.name}`} disabled={busy}
              onClick={() => execute(() => updateDevice(device.id, 'approve'))}>Approve</Button>}
            {device.status !== 'REVOKED' && device.id !== ownDevice.id && <Button aria-label={`Revoke ${device.name}`} disabled={busy}
              onClick={() => setConfirmation({ label: `Revoke ${device.name}?`, action: () => updateDevice(device.id, 'revoke') })}>Revoke</Button>}
          </div>
        </details>)}
      </Panel>
      <Panel icon={<Activity size={17} />} title="Active sessions" detail="Sessions observed by this workspace">
        {dashboard.data.sessions.map(entry => <div className="operation-item session-row" key={entry.id}>
          <div><strong>{entry.username}</strong><span>{formatTimestamp(entry.startedAt)}</span></div>
          {entry.id !== session?.id && <Button aria-label={`Terminate session for ${entry.username}`} disabled={busy}
            onClick={() => setConfirmation({ label: `Terminate the session for ${entry.username}?`, action: () => terminateSession(entry.id) })}>Terminate</Button>}
        </div>)}
      </Panel>
      <Panel icon={<Activity size={17} />} title="Recent activity" detail="Observations from this server process">
        {dashboard.data.activity.length === 0 && <Notice kind="empty">No operational events yet.</Notice>}
        <ul className="operation-list">{dashboard.data.activity.map(event => <li key={event.id}>
          {event.type.toLowerCase().replaceAll('_', ' ')} <span>{formatTimestamp(event.timestamp)}</span>
        </li>)}</ul>
      </Panel>
    </div>}
  </>
}
