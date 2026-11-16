import { useEffect, useState } from 'react'
import { MessageSquare } from 'lucide-react'
import { ApiError } from '../../services/http'
import { acknowledge, history, receipts, sendFixture, type Envelope, type Receipt } from '../../services/messaging'
import { Button, Notice, Panel } from '../ui/Primitives'

const FIXTURES = [
  { label: 'Fixture A', payload: new Uint8Array([0, 255, 4, 0]) },
  { label: 'Fixture B', payload: new Uint8Array([0, 254, 8, 0]) },
]
const encoded = (value: Uint8Array) => btoa(String.fromCharCode(...value))

function merge(previous: Envelope[], incoming: Envelope[]) {
  return [...new Map([...previous, ...incoming].map(row => [row.sequence, row])).values()]
    .sort((a, b) => b.sequence - a.sequence)
}

export function FixtureConversation({ id, userId, title }: { id: string; userId: number; title: string }) {
  const [items, setItems] = useState<Envelope[]>([])
  const [fixture, setFixture] = useState(0)
  const [send, setSend] = useState<{ index: number; key: string }>()
  const [failure, setFailure] = useState('')
  const [pollFailure, setPollFailure] = useState(false)
  const [loading, setLoading] = useState(true)
  const [sending, setSending] = useState(false)
  const [older, setOlder] = useState(false)
  const [loadingOlder, setLoadingOlder] = useState(false)
  const [denied, setDenied] = useState(false)
  const [inspected, setInspected] = useState<number>()
  const [observed, setObserved] = useState<Receipt[]>([])
  const [actions, setActions] = useState(new Set<number>())
  const [revision, setRevision] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    const pending = new Set<number>()
    let latest: number | undefined
    let timer: number | undefined
    let ticks = 0
    async function reconcile() {
      try {
        const rows = await history(id, latest ? `?after=${latest}` : '', AbortSignal.any([controller.signal, AbortSignal.timeout(8000)]))
        if (controller.signal.aborted) return
        if (latest === undefined) setOlder(rows.length === 50)
        if (rows.length) latest = Math.max(latest ?? 0, ...rows.map(row => row.sequence))
        setItems(current => merge(current, rows))
        if (++ticks % 30 === 0) {
          const cutoff = Date.now() - (30 * 24 * 60 * 60 * 1000)
          setItems(current => current.filter(row => Date.parse(row.acceptedAt) >= cutoff))
        }
        setLoading(false)
        setDenied(false)
        setPollFailure(false)
        for (const row of rows) {
          if (row.senderUserId !== userId) pending.add(row.sequence)
        }
        for (const sequence of [...pending].slice(0, 50)) {
          if (controller.signal.aborted) return
          try { await acknowledge(id, sequence, 'DELIVERED'); pending.delete(sequence) }
          catch (error) {
            if (error instanceof ApiError && [401, 403, 404, 410].includes(error.status)) pending.delete(sequence)
            else break
          }
        }
      } catch (error) {
        if (controller.signal.aborted) return
        if (error instanceof ApiError && [401, 403, 404].includes(error.status)) {
          setItems([])
          setDenied(true)
          return
        }
        setPollFailure(true)
      }
      if (!controller.signal.aborted) timer = window.setTimeout(reconcile, 1000)
    }
    void reconcile()
    return () => { controller.abort(); window.clearTimeout(timer) }
  }, [id, userId, revision])

  useEffect(() => {
    if (inspected === undefined) return
    const controller = new AbortController()
    let timer: number | undefined
    async function load() {
      try {
        const result = await receipts(id, inspected!, AbortSignal.any([controller.signal, AbortSignal.timeout(8000)]))
        if (!controller.signal.aborted) setObserved(result)
      } catch {
        if (!controller.signal.aborted) setObserved([])
      }
      if (!controller.signal.aborted) timer = window.setTimeout(load, 1500)
    }
    void load()
    return () => { controller.abort(); window.clearTimeout(timer) }
  }, [id, inspected])

  async function submit() {
    const attempt = send ?? { index: fixture, key: crypto.randomUUID() }
    setSend(attempt)
    setSending(true)
    setFailure('')
    try {
      const row = await sendFixture(id, attempt.key, FIXTURES[attempt.index].payload)
      setItems(current => merge(current, [row]))
      setSend(undefined)
    } catch {
      setFailure('Submission could not be confirmed. Retry the same fixture.')
    } finally {
      setSending(false)
    }
  }

  async function loadOlder() {
    const oldest = items.at(-1)?.sequence
    if (!oldest) return
    setLoadingOlder(true)
    setFailure('')
    try {
      const rows = await history(id, `?before=${oldest}`)
      for (const row of rows.filter(item => item.senderUserId !== userId)) {
        await acknowledge(id, row.sequence, 'DELIVERED')
      }
      setItems(current => merge(current, rows))
      setOlder(rows.length === 50)
    } catch {
      setFailure('Older envelopes could not be retrieved.')
    } finally {
      setLoadingOlder(false)
    }
  }

  async function read(sequence: number) {
    try {
      await acknowledge(id, sequence, 'READ')
      setActions(current => new Set([...current, sequence]))
    } catch { setFailure('Read acknowledgement could not be confirmed.') }
  }

  return <Panel title={title} detail="Synthetic envelopes · development transport" icon={<MessageSquare size={17} />}>
    {denied ? <Notice kind="error" onRetry={() => setRevision(value => value + 1)}>Conversation access is unavailable.</Notice> : <>
      {failure && <Notice kind="error">{failure}</Notice>}
      {pollFailure && <Notice kind="error">The conversation could not refresh. It will retry automatically.</Notice>}
      {loading ? <Notice kind="loading">Loading envelope history…</Notice>
        : items.length === 0 && <Notice kind="empty">No envelopes yet.</Notice>}
      {older && <Button onClick={loadOlder} disabled={loadingOlder}>Load older envelopes</Button>}
      <ol className="operation-list">
        {items.map(item => <li key={item.sequence}>
          <span>
            {FIXTURES.find(row => encoded(row.payload) === item.envelope)?.label ?? 'Opaque envelope'}
            {' · #'}{item.sequence}{item.senderUserId === userId ? ' · You' : ' · Participant'}
          </span>
          {item.senderUserId !== userId && <Button onClick={() => read(item.sequence)} disabled={actions.has(item.sequence)}>
            {actions.has(item.sequence) ? 'Read recorded' : 'Mark read'}
          </Button>}
          <Button onClick={() => { setObserved([]); setInspected(item.sequence) }}>View receipts</Button>
        </li>)}
      </ol>
      {inspected !== undefined && <p aria-live="polite">Envelope #{inspected}: {observed.length
        ? observed.map(row => `Account ${row.recipientUserId}: ${row.readAt ? 'Read' : 'Delivered'}`).join(', ')
        : 'No visible acknowledgements yet.'}</p>}
      <form className="device-form" onSubmit={event => { event.preventDefault(); if (!sending) void submit() }}>
        <label>Test fixture<select value={send?.index ?? fixture} disabled={sending || !!send}
          onChange={event => setFixture(Number(event.target.value))}>
          {FIXTURES.map((item, index) => <option key={item.label} value={index}>{item.label}</option>)}
        </select></label>
        <Button type="submit" disabled={sending}>{send ? 'Retry fixture' : 'Send synthetic envelope'}</Button>
      </form>
    </>}
  </Panel>
}
