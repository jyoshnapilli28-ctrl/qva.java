import { useEffect, useState } from 'react'
import { addParticipant, participants, removeParticipant, type Group, type Participant } from '../../services/messaging'
import type { Member } from '../../types'
import { Button, Notice } from '../ui/Primitives'

export function GroupParticipants({ group, userId, roster }: { group: Group; userId: number; roster: Member[] }) {
  const [members, setMembers] = useState<Participant[]>()
  const [selected, setSelected] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(false)
  const [revision, setRevision] = useState(0)
  useEffect(() => {
    const c = new AbortController()
    participants(group.id, AbortSignal.any([c.signal, AbortSignal.timeout(8000)]))
      .then(items => { if (!c.signal.aborted) setMembers(items) })
      .catch(() => { if (!c.signal.aborted) { setMembers(undefined); setError(true) } })
    return () => c.abort()
  }, [group.id, revision])

  async function change(action: () => Promise<unknown>) {
    setBusy(true)
    setError(false)
    try { await action(); setSelected(''); setRevision(current => current + 1) }
    catch { setError(true) }
    finally { setBusy(false) }
  }
  return <section aria-label="Group participants">
    <h3>Group participants</h3>
    {error && <Notice kind="error" onRetry={() => { setError(false); setRevision(value => value + 1) }}>
      Group participants could not be updated.
    </Notice>}
    {!error && !members && <Notice kind="loading">Loading participants…</Notice>}
    {members && <ul className="operation-list">{members.map(member => <li key={member.id}>
      {member.name}
      {group.ownerId === userId && member.id !== userId && <Button disabled={busy}
        onClick={() => change(() => removeParticipant(group.id, member.id))}>Remove {member.name}</Button>}
    </li>)}</ul>}
    {members && !error && group.ownerId === userId && <form className="device-form" onSubmit={event => {
      event.preventDefault()
      if (!busy && selected) void change(() => addParticipant(group.id, Number(selected)))
    }}>
      <label>Add group participant<select value={selected} onChange={event => setSelected(event.target.value)}>
        <option value="">Choose a member</option>
        {roster.filter(member => !members.some(value => value.id === member.id))
          .map(member => <option key={member.id} value={member.id}>{member.displayName}</option>)}
      </select></label>
      <Button type="submit" disabled={busy || !selected}>Add participant</Button>
    </form>}
  </section>
}
