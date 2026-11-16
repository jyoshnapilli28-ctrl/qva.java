import { useEffect, useState, type FormEvent } from 'react'
import { FixtureConversation } from '../components/operations/FixtureConversation'
import { GroupParticipants } from '../components/operations/GroupParticipants'
import { MessageSquare } from 'lucide-react'
import { PageHeader } from '../components/PageHeader'
import { Button, Notice, Panel } from '../components/ui/Primitives'
import type { NetworkSnapshot } from '../hooks/useNetworkSnapshot'
import type { Operations } from '../hooks/useOperations'
import { conversations, createConversation, createGroup, groups, relayStatus, type Conversation, type Group } from '../services/messaging'
import { formatTimestamp } from '../services/operations'

type RelayView =
  | { kind: 'loading' | 'disabled' | 'error' }
  | { kind: 'ready'; items: Conversation[]; groupItems: Group[] }

export function MessagesPage({ operations, snapshot }: { operations: Operations; snapshot: NetworkSnapshot }) {
  const [view, setView] = useState<RelayView>({ kind: 'loading' })
  const [revision, setRevision] = useState(0)
  const [busy, setBusy] = useState(false)
  const [peer, setPeer] = useState('')
  const [title, setTitle] = useState('')
  const [active, setActive] = useState<string>()
  const [failure, setFailure] = useState(false)
  const identity = operations.identity
  const deviceId = identity.status === 'ready' && identity.data.device?.status === 'APPROVED'
    ? identity.data.device.id : null

  useEffect(() => {
    if (!deviceId) { setView({ kind: 'loading' }); return }
    const controller = new AbortController()
    const signal = AbortSignal.any([controller.signal, AbortSignal.timeout(8000)])
    setView({ kind: 'loading' })
    async function load() {
      try {
        const status = await relayStatus(signal)
        let current: RelayView = { kind: 'disabled' }
        if (status.enabled) {
          const [items, groupItems] = await Promise.all([conversations(signal), groups(signal)])
          current = { kind: 'ready', items, groupItems }
        }
        if (!controller.signal.aborted) setView(current)
      } catch {
        if (!controller.signal.aborted) setView({ kind: 'error' })
      }
    }
    void load()
    return () => controller.abort()
  }, [deviceId, revision])

  async function start(event: FormEvent) {
    event.preventDefault()
    if (busy || !peer) return
    setBusy(true)
    setFailure(false)
    try {
      setActive((await createConversation(Number(peer))).id)
      setRevision(current => current + 1)
    } catch {
      setFailure(true)
    } finally {
      setBusy(false)
    }
  }

  async function startGroup(event: FormEvent) {
    event.preventDefault()
    if (busy || !title.trim()) return
    setBusy(true)
    setFailure(false)
    try {
      setActive((await createGroup(title)).id)
      setTitle('')
      setRevision(current => current + 1)
    } catch { setFailure(true) }
    finally { setBusy(false) }
  }

  const selectedGroup = view.kind === 'ready' ? view.groupItems.find(group => group.id === active) : undefined
  const selectedPeer = view.kind === 'ready' ? view.items.find(item => item.id === active) : undefined
  const userId = identity.status === 'ready' ? identity.data.device?.userId : undefined
  const roster = snapshot.members.status === 'ready' ? snapshot.members.data : []

  return <>
    <PageHeader title="Direct conversations" detail="Direct and group transport for approved devices." onRefresh={() => setRevision(value => value + 1)} />
    <Panel icon={<MessageSquare size={17} />} title="Conversations" detail="Development relay">
      {identity.status === 'loading' && <Notice kind="loading">Checking device approval…</Notice>}
      {identity.status === 'error' && <Notice kind="error" onRetry={operations.refresh}>Device status is unavailable.</Notice>}
      {identity.status === 'ready' && !deviceId && <Notice kind="info">An approved device is required.</Notice>}
      {deviceId && view.kind === 'loading' && <Notice kind="loading">Loading conversations…</Notice>}
      {deviceId && view.kind === 'disabled' && <Notice kind="info">The development relay is disabled on this server.</Notice>}
      {deviceId && view.kind === 'error' && <Notice kind="error" onRetry={() => setRevision(current => current + 1)}>
        Conversations are unavailable.
      </Notice>}
      {deviceId && view.kind === 'ready' && <>
        <Notice kind="info">Synthetic envelopes only. Personal messages require an approved encrypted client.</Notice>
        {snapshot.members.status === 'ready' && <form className="device-form" onSubmit={start}>
          <label>Participant
            <select value={peer} onChange={event => setPeer(event.target.value)} required>
              <option value="">Choose a member</option>
              {snapshot.members.data.filter(item => identity.status === 'ready' && item.username !== identity.data.username)
                .map(item => <option key={item.id} value={item.id}>{item.displayName}</option>)}
            </select>
          </label>
          <Button type="submit" disabled={busy || !peer}>Open conversation</Button>
        </form>}
        {identity.status === 'ready' && identity.data.role === 'HOST' && <form className="device-form" onSubmit={startGroup}>
          <label>Group name<input value={title} maxLength={60} onChange={event => setTitle(event.target.value)} required /></label>
          <Button type="submit" disabled={busy || !title.trim()}>Create group</Button>
        </form>}
        {failure && <Notice kind="error">The conversation could not be opened. Check your device status and retry.</Notice>}
        {view.items.length === 0 ? <Notice kind="empty">No direct conversations yet.</Notice>
          : <ul className="operation-list">{view.items.map(item => <li key={item.id}>
            <Button onClick={() => setActive(item.id)} aria-pressed={active === item.id}>{item.peerName}</Button>
            <span>{formatTimestamp(item.createdAt)}</span>
          </li>)}</ul>}
        {view.groupItems.length > 0 && <ul className="operation-list">{view.groupItems.map(group => <li key={group.id}>
          <Button onClick={() => setActive(group.id)} aria-pressed={active === group.id}>Group: {group.name}</Button>
        </li>)}</ul>}
      </>}
    </Panel>
    {deviceId && userId && selectedGroup && <GroupParticipants key={selectedGroup.id}
      group={selectedGroup} userId={userId} roster={roster} />}
    {deviceId && userId && active && (selectedGroup || selectedPeer) && <FixtureConversation key={active}
      id={active} userId={userId} title={selectedGroup?.name ?? selectedPeer?.peerName ?? 'Conversation'} />}
  </>
}
