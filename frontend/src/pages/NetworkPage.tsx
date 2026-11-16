import type { Operations } from '../hooks/useOperations'
import { PageHeader } from '../components/PageHeader'
import { SnapshotCards } from '../components/network/SnapshotCards'
import { TopologyCanvas } from '../components/network/TopologyCanvas'
import { Notice } from '../components/ui/Primitives'
import type { NetworkSnapshot } from '../hooks/useNetworkSnapshot'
import { topologyFromHealth } from '../services/network'

export function NetworkPage({ snapshot, operations }: { snapshot: NetworkSnapshot; operations: Operations }) {
  return <>
    <PageHeader title="Network status" detail="Inspect the relay and application connectivity."
      refreshing={snapshot.refreshing} onRefresh={() => { snapshot.refresh(); operations.refresh() }} />
    <SnapshotCards health={snapshot.health} members={snapshot.members} />
    {snapshot.health.status === 'loading' && <Notice kind="loading">Checking relay health…</Notice>}
    {snapshot.health.status === 'error' && <Notice kind="error" onRetry={snapshot.refresh}>Relay health is unavailable.</Notice>}
    {operations.topology.status === 'loading' && <Notice kind="loading">Reading device topology…</Notice>}
    {operations.topology.status === 'error' && <Notice kind="error" onRetry={operations.refresh}>Device topology is unavailable.</Notice>}
    <TopologyCanvas topology={topologyFromHealth(snapshot.health)}
      devices={operations.topology.status === 'ready' ? operations.topology.data.devices : undefined} />
  </>
}
