import { Users } from 'lucide-react'
import type { Member, Resource } from '../../types'
import { Avatar, Notice, Panel } from '../ui/Primitives'

export function MemberRoster({ members, onRetry }: { members: Resource<Member[]>; onRetry: () => void }) {
  return <Panel icon={<Users size={17} />} title="Member presence" detail="Account presence from classic chat">
    {members.status === 'loading' && <Notice kind="loading">Loading member presence…</Notice>}
    {members.status === 'error' && <Notice kind="error" onRetry={onRetry}>Member presence is unavailable.</Notice>}
    {members.status === 'ready' && (members.data.length === 0
      ? <Notice kind="empty">No active accounts were returned.</Notice>
      : <ul className="member-list">{members.data.map(member => <li key={member.id}>
        <Avatar name={member.displayName} />
        <div><strong>{member.displayName}</strong><small>@{member.username}</small></div>
        <span className={member.online ? 'presence online' : 'presence'}><i aria-hidden="true" />{member.online ? 'Online' : 'Offline'}</span>
      </li>)}</ul>)}
  </Panel>
}
