import { useEffect, useState } from 'react'
import { Copy, Ticket } from 'lucide-react'
import { Button, Notice, Panel } from '../ui/Primitives'
import { createInvitation, getInvitationAudit, getInvitations, revokeInvitation,
  type CreatedInvitation, type Invitation, type InvitationAudit } from '../../services/invitations'

export function InvitationManager() {
  const [invitations, setInvitations] = useState<Invitation[]>()
  const [audit, setAudit] = useState<InvitationAudit[]>()
  const [created, setCreated] = useState<CreatedInvitation>()
  const [hours, setHours] = useState(168)
  const [pendingRevoke, setPendingRevoke] = useState<string>()
  const [copied, setCopied] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(false)

  async function refresh() {
    try {
      const [list, events] = await Promise.all([getInvitations(), getInvitationAudit()])
      setInvitations(list); setAudit(events); setError(false)
    }
    catch { setError(true) }
  }

  useEffect(() => { void refresh() }, [])

  async function issue() {
    setBusy(true); setError(false); setCreated(undefined); setCopied(false)
    try { setCreated(await createInvitation(hours)); await refresh() }
    catch { setError(true) }
    finally { setBusy(false) }
  }

  async function revoke(id: string) {
    setBusy(true); setError(false)
    try { await revokeInvitation(id); setPendingRevoke(undefined); await refresh() }
    catch { setError(true) }
    finally { setBusy(false) }
  }

  const invitationLink = created
    ? `${window.location.origin}/app/register#invite=${encodeURIComponent(created.token)}` : ''

  return <Panel icon={<Ticket size={17} />} title="Member invitations"
    detail="Single-use links. Share each link privately; the token is shown only once.">
    {error && <Notice kind="error" onRetry={() => { void refresh() }}>Invitation service is unavailable or the requested change was rejected.</Notice>}
    <div className="operation-actions">
      <label>Expires after<select value={hours} onChange={event => setHours(Number(event.target.value))} disabled={busy}>
        <option value={24}>24 hours</option><option value={168}>7 days</option><option value={720}>30 days</option>
      </select></label>
      <Button variant="primary" disabled={busy} onClick={() => void issue()}>{busy ? 'Working…' : 'Create invitation'}</Button>
    </div>
    {created && <Notice kind="info">
      <strong>Copy this invitation now.</strong> It cannot be retrieved later. Send it only to the intended member.
      <label>Private invitation link<input readOnly value={invitationLink} onFocus={event => event.currentTarget.select()} /></label>
      <div className="operation-actions"><Button onClick={() => {
        void navigator.clipboard.writeText(invitationLink).then(() => setCopied(true)).catch(() => setCopied(false))
      }}><Copy size={15} aria-hidden="true" /> {copied ? 'Copied' : 'Copy link'}</Button>
        <Button onClick={() => setCreated(undefined)}>Hide link</Button></div>
    </Notice>}
    {!invitations && !error && <Notice kind="loading">Loading invitations…</Notice>}
    {invitations && invitations.length === 0 && <Notice kind="empty">No invitations have been created.</Notice>}
    {invitations && <ul className="operation-list">{invitations.map(invite => <li className="invitation-row" key={invite.id}>
      <span>{invite.status} · expires {new Date(invite.expiresAt).toLocaleString()}
        {invite.redeemedUsername && ` · used by @${invite.redeemedUsername}`}</span>
      {invite.status === 'PENDING' && (pendingRevoke === invite.id
        ? <span className="operation-actions"><Button disabled={busy} onClick={() => void revoke(invite.id)}>Confirm revoke</Button>
          <Button disabled={busy} onClick={() => setPendingRevoke(undefined)}>Cancel</Button></span>
        : <Button disabled={busy} onClick={() => setPendingRevoke(invite.id)}>Revoke</Button>)}
    </li>)}</ul>}
    {audit && <details className="operation-item"><summary>Invitation audit ({audit.length})</summary>
      {audit.length === 0 ? <Notice kind="empty">No invitation events have been recorded.</Notice>
        : <ul className="operation-list">{audit.map(event => <li key={event.id}>
          {event.action} · {event.actorUsername ? `@${event.actorUsername}` : 'unknown operator'}
          {event.affectedUsername ? ` → @${event.affectedUsername}` : ''} · {new Date(event.occurredAt).toLocaleString()}
        </li>)}</ul>}
    </details>}
  </Panel>
}
