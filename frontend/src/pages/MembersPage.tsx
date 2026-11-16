import { PageHeader } from '../components/PageHeader'
import { MemberRoster } from '../components/network/MemberRoster'
import type { NetworkSnapshot } from '../hooks/useNetworkSnapshot'

export function MembersPage({ snapshot }: { snapshot: NetworkSnapshot }) {
  return <>
    <PageHeader title="Workspace members" detail="View the active QVA account roster and chat presence."
      refreshing={snapshot.refreshing} onRefresh={snapshot.refresh} />
    <MemberRoster members={snapshot.members} onRetry={snapshot.refresh} />
  </>
}
