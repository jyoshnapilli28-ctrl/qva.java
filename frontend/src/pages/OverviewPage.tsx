import { Activity, ArrowRight } from 'lucide-react'
import { Link } from 'react-router-dom'
import { IdentityPanel } from '../components/operations/IdentityPanel'
import type { Operations } from '../hooks/useOperations'
import { PageHeader } from '../components/PageHeader'
import { MemberRoster } from '../components/network/MemberRoster'
import { SnapshotCards } from '../components/network/SnapshotCards'
import { Notice, Panel } from '../components/ui/Primitives'
import type { NetworkSnapshot } from '../hooks/useNetworkSnapshot'

export function OverviewPage({ snapshot, operations }: { snapshot: NetworkSnapshot; operations: Operations }) {
  return <>
    <PageHeader title="Operations overview" detail="Check service health and see who is connected to QVA."
      refreshing={snapshot.refreshing} onRefresh={snapshot.refresh} />
    <SnapshotCards health={snapshot.health} members={snapshot.members} />
    <div className="panel-grid operations-grid">
      <IdentityPanel operations={operations} />
      <Panel icon={<Activity size={17} />} title="Connection snapshot" detail="Observed application state">
        {snapshot.health.status === 'error' && <Notice kind="error" onRetry={snapshot.refresh}>Relay health is unavailable.</Notice>}
        <dl className="detail-list">
          <div><dt>Web session</dt><dd>Authenticated</dd></div>
          <div><dt>VPN connectivity</dt><dd>Unverified</dd></div>
          <div><dt>Snapshot time</dt><dd>{snapshot.updated?.toLocaleTimeString() ?? 'Unavailable'}</dd></div>
        </dl>
        <Link className="text-link" to="/network">View network status <ArrowRight size={15} aria-hidden="true" /></Link>
      </Panel>
      <MemberRoster members={snapshot.members} onRetry={snapshot.refresh} />
    </div>
  </>
}
