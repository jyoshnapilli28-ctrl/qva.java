import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, ExternalLink, Network } from 'lucide-react'
import { Brand, classicUrl } from '../components/Brand'
import { Button, Notice } from '../components/ui/Primitives'
import { signIn } from '../services/auth'
import { isUnauthenticated } from '../services/http'
import type { Session } from '../types'

export function LoginPage({ onLogin, expired }: { onLogin: (session: Session) => void; expired?: boolean }) {
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const heading = useRef<HTMLHeadingElement>(null)
  const password = useRef<HTMLInputElement>(null)

  useEffect(() => { if (expired) heading.current?.focus() }, [expired])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return
    const data = new FormData(event.currentTarget)
    const username = String(data.get('username')).trim()
    if (!username) {
      setError('Enter your username.')
      return
    }
    setBusy(true)
    setError('')
    try {
      onLogin(await signIn(username, String(data.get('password'))))
    } catch (failure) {
      setError(isUnauthenticated(failure) ? 'Invalid username or password.'
        : 'Unable to sign in. Check your connection and try again.')
    } finally {
      if (password.current) password.current.value = ''
      setBusy(false)
    }
  }

  return <main className="login">
    <div className="login-visual" aria-hidden="true">
      <div className="signal-orbit"><div className="orbit-inner"><Network size={58} strokeWidth={1.15} /></div><i /><i /><i /></div>
      <div className="visual-caption"><span className="dot" /> PRIVATE NETWORK OPERATIONS</div>
    </div>
    <div className="login-panel">
      <Brand />
      <div className="login-form">
        <h1 ref={heading} tabIndex={-1}>Sign in to QVA</h1>
        <p className="muted">Use your existing QVA account to access the workspace.</p>
        {expired && <Notice kind="info">Your session expired. Sign in again to continue.</Notice>}
        <form onSubmit={submit} aria-busy={busy}>
          <label>Username<input name="username" autoComplete="username" required /></label>
          <label>Password<input ref={password} name="password" type="password" autoComplete="current-password" required /></label>
          {error && <Notice kind="error">{error}</Notice>}
          <Button variant="primary" disabled={busy} type="submit">
            {busy ? 'Signing in…' : 'Sign in'}<ArrowRight size={17} aria-hidden="true" />
          </Button>
        </form>
        <a className="legacy" href={classicUrl}>Open classic QVA <ExternalLink size={13} aria-hidden="true" /></a>
        <p className="muted">Have a private QVA invitation? <Link className="text-link" to="/register">Create an account</Link></p>
      </div>
    </div>
  </main>
}
