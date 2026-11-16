import { Activity, Server, Users } from 'lucide-react'
import type { ReactNode } from 'react'
import type { NetworkSnapshot } from '../../hooks/useNetworkSnapshot'

function Metric({ label, value, detail, icon }: {
  label: string; value: ReactNode; detail: string; icon: ReactNode
}) {
  return <article className="card metric">
    <div className="metric-label"><span aria-hidden="true">{icon}</span>{label}</div>
    <strong>{value}</strong><small>{detail}</small>
  </article>
}

function formatUptime(seconds: number) {
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor(seconds % 3600 / 60)
  const remainder = seconds % 60
  if (hours) return `${hours}h ${minutes}m`
  if (minutes) return `${minutes}m ${remainder}s`
  return `${remainder}s`
}

export function SnapshotCards({ health, members }: Pick<NetworkSnapshot, 'health' | 'members'>) {
  const healthFallback = health.status === 'loading' ? 'Loading…' : 'Unavailable'
  const rosterFallback = members.status === 'loading' ? 'Loading…' : 'Unavailable'
  return <div className="metrics">
    <Metric label="Relay status" value={health.status === 'ready'
      ? (health.data.status === 'UP' ? 'Operational' : health.data.status) : healthFallback}
      detail="Spring Boot service" icon={<Server size={16} />} />
    <Metric label="Members online" value={members.status === 'ready'
      ? `${members.data.filter(member => member.online).length} / ${members.data.length}` : rosterFallback}
      detail="Classic WebSocket presence" icon={<Users size={16} />} />
    <Metric label="Service uptime" value={health.status === 'ready'
      ? formatUptime(health.data.uptimeSeconds) : healthFallback}
      detail={health.status === 'ready' ? `Backend v${health.data.version}` : 'Service health endpoint'}
      icon={<Activity size={16} />} />
  </div>
}
