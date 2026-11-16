import { useEffect } from 'react'
import { Link, Route, Routes } from 'react-router-dom'
import { workspaceRoutes } from './app/routes'
import { Notice } from './components/ui/Primitives'
import { useNetworkSnapshot } from './hooks/useNetworkSnapshot'
import { useOperations } from './hooks/useOperations'
import { useSession } from './hooks/useSession'
import { WorkspaceLayout } from './layouts/WorkspaceLayout'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import type { Session } from './types'

function Workspace({ session, onExit, onExpired }: {
  session: Session; onExit: () => void; onExpired: () => void
}) {
  const snapshot = useNetworkSnapshot(onExpired)
  const operations = useOperations(onExpired)
  const identity = operations.identity
  const canManage = identity.status === 'ready' && identity.data.role === 'HOST'
    && identity.data.device?.status === 'APPROVED'
  return <Routes>
    <Route element={<WorkspaceLayout session={session} onExit={onExit} canManage={canManage} />}>
      {workspaceRoutes.map(({ path, Page }) => <Route key={path} path={path} element={<Page snapshot={snapshot} operations={operations} />} />)}
      <Route path="*" element={<><h1>Page unavailable</h1><Link className="text-link" to="/">Back to overview</Link></>} />
    </Route>
  </Routes>
}

export function App() {
  const session = useSession()
  const status = session.state.status

  useEffect(() => {
    if (status !== 'signed-in') document.title = 'QVA · Sign in'
  }, [status])

  switch (session.state.status) {
    case 'checking':
      return <main className="loading"><Notice kind="loading">Checking your QVA session…</Notice></main>
    case 'unavailable':
      return <main className="loading"><Notice kind="error" onRetry={session.retry}>Unable to check your QVA session.</Notice></main>
    case 'signed-out':
      return <Routes>
        <Route path="/register" element={<RegisterPage />} />
        <Route path="*" element={<LoginPage onLogin={session.signedIn} expired={session.state.expired} />} />
      </Routes>
    case 'signed-in':
      return <Workspace session={session.state.session} onExit={session.signedOut} onExpired={session.expired} />
  }
}
