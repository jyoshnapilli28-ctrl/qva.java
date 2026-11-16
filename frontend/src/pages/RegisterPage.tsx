import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { Brand } from '../components/Brand'
import { Button, Notice } from '../components/ui/Primitives'
import { registerAccount } from '../services/invitations'

function initialInvite() {
  return new URLSearchParams(window.location.hash.slice(1)).get('invite') ?? ''
}

export function RegisterPage() {
  const [token, setToken] = useState(initialInvite)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [created, setCreated] = useState('')

  useEffect(() => {
    if (initialInvite()) {
      window.history.replaceState(window.history.state, '', window.location.pathname + window.location.search)
    }
  }, [])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return
    const data = new FormData(event.currentTarget)
    const input = event.currentTarget
    const values = {
      token: token.trim(),
      username: String(data.get('username') ?? '').trim(),
      displayName: String(data.get('displayName') ?? '').trim(),
      password: String(data.get('password') ?? ''),
    }
    input.reset()
    setBusy(true)
    setError('')
    try {
      const account = await registerAccount(values)
      setToken('')
      setCreated(account.username)
    } catch (failure) {
      setError(failure instanceof Error && 'status' in failure && failure.status === 409
        ? 'This invitation is unavailable, expired, or already used, or the username is taken.'
        : 'Account creation failed. Check the invitation and try again.')
    } finally {
      setBusy(false)
    }
  }

  return <main className="login">
    <div className="login-visual" aria-hidden="true"><div className="signal-orbit"><div className="orbit-inner">Q</div><i /><i /><i /></div>
      <div className="visual-caption"><span className="dot" /> PRIVATE MEMBER REGISTRATION</div></div>
    <div className="login-panel"><Brand /><div className="login-form">
      <h1>Create your QVA account</h1>
      <p className="muted">Registration requires a private, unused invitation. New accounts have MEMBER access and devices require host approval.</p>
      {created ? <>
        <Notice kind="info">Account @{created} was created as a member. Sign in, then register this browser for host approval.</Notice>
        <Link className="text-link" to="/">Return to sign in</Link>
      </> : <form onSubmit={submit} aria-busy={busy}>
        <label>Invitation token<input value={token} onChange={event => setToken(event.target.value)} type="password"
          autoComplete="off" minLength={43} maxLength={43} required /></label>
        <label>Username<input name="username" autoComplete="username" minLength={3} maxLength={32} pattern={'[A-Za-z0-9._\\-]+'} required /></label>
        <label>Display name<input name="displayName" autoComplete="name" minLength={1} maxLength={60} required /></label>
        <label>Password<input name="password" type="password" autoComplete="new-password" minLength={12} maxLength={72} required /></label>
        {error && <Notice kind="error">{error}</Notice>}
        <Button variant="primary" type="submit" disabled={busy}>{busy ? 'Creating account…' : 'Create member account'}</Button>
        <Link className="legacy" to="/">Back to sign in</Link>
      </form>}
    </div></div>
  </main>
}
