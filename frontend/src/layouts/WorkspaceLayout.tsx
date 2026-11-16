import { useEffect, useRef, useState } from 'react'
import { ArrowUpRight, LogOut, ShieldCheck } from 'lucide-react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { workspaceRoutes } from '../app/routes'
import { Brand, classicUrl } from '../components/Brand'
import { Avatar, Button, Notice } from '../components/ui/Primitives'
import { signOut } from '../services/auth'
import { isUnauthenticated } from '../services/http'
import type { Session } from '../types'
import { lockWorkspaceVault, workspaceVaultEnabled } from '../vault/lifecycle'

export function WorkspaceLayout({ session, onExit, canManage }: { session: Session; onExit: () => void; canManage: boolean }) {
  const { pathname } = useLocation()
  const content = useRef<HTMLElement>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(false)
  const activeRoute = workspaceRoutes.find(item => item.path === pathname)

  useEffect(() => {
    document.title = activeRoute?.title ?? 'QVA · Workspace'
    content.current?.focus()
  }, [activeRoute, pathname])

  async function logout() {
    if (workspaceVaultEnabled) lockWorkspaceVault()
    setBusy(true)
    setError(false)
    try {
      await signOut()
      onExit()
    } catch (failure) {
      if (isUnauthenticated(failure)) onExit()
      else { setError(true); setBusy(false) }
    }
  }

  return <div className="workspace">
    <a className="skip-link" href="#main-content" onClick={event => {
      event.preventDefault()
      content.current?.focus()
    }}>Skip to main content</a>
    <aside className="rail">
      <Brand />
      <nav aria-label="Workspace">
        <p>WORKSPACE</p>
        {workspaceRoutes.filter(route => !('hostOnly' in route) || canManage).map(({ path, label, icon: Icon }) =>
          <NavLink end key={path} to={path}><Icon size={17} aria-hidden="true" />{label}</NavLink>)}
      </nav>
      <div className="rail-bottom"><a className="legacy" href={classicUrl}>Classic QVA <ArrowUpRight size={14} aria-hidden="true" /></a></div>
    </aside>
    <div className="canvas">
      <header className="topbar">
        <span className="breadcrumb">Workspace <span aria-hidden="true">/</span> {activeRoute?.label}</span>
        <div className="account"><Avatar name={session.username} /><span>{session.username}</span>
          <Button variant="icon-button" onClick={logout} disabled={busy} aria-label="Sign out"><LogOut size={17} aria-hidden="true" /></Button>
        </div>
      </header>
      <main className="content" id="main-content" ref={content} tabIndex={-1}>
        {error && <Notice kind="error" onRetry={logout}>Sign out could not be confirmed.</Notice>}
        <Outlet />
        <footer className="footer"><ShieldCheck size={15} aria-hidden="true" /> Signed in as {session.username}<span>Refreshes every 15 seconds</span></footer>
      </main>
    </div>
  </div>
}
