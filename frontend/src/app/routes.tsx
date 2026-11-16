import { KeyRound, LayoutDashboard, MessageSquare, Network, ShieldCheck, Users } from 'lucide-react'
import { OverviewPage } from '../pages/OverviewPage'
import { NetworkPage } from '../pages/NetworkPage'
import { HostPage } from '../pages/HostPage'
import { MessagesPage } from '../pages/MessagesPage'
import { MembersPage } from '../pages/MembersPage'
import { VaultPage } from '../pages/VaultPage'
import { workspaceVaultEnabled } from '../vault/lifecycle'

export const workspaceRoutes = [
  { path: '/', label: 'Overview', title: 'QVA · Overview', icon: LayoutDashboard, Page: OverviewPage },
  { path: '/network', label: 'Network', title: 'QVA · Network', icon: Network, Page: NetworkPage },
  { path: '/members', label: 'Members', title: 'QVA · Members', icon: Users, Page: MembersPage },
  { path: '/messages', label: 'Messages', title: 'QVA · Messages', icon: MessageSquare, Page: MessagesPage },
  ...(workspaceVaultEnabled ? [{ path: '/vault', label: 'Local vault', title: 'QVA · Local vault', icon: KeyRound, Page: VaultPage }] : []),
  { path: '/host', label: 'Host dashboard', title: 'QVA · Host dashboard', icon: ShieldCheck, Page: HostPage, hostOnly: true },
] as const
