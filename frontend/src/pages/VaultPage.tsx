import { useRef, useState, type FormEvent } from 'react'
import { KeyRound } from 'lucide-react'
import { PageHeader } from '../components/PageHeader'
import { Button, Notice, Panel } from '../components/ui/Primitives'
import type { Operations } from '../hooks/useOperations'
import { minimumPassword, maximumPassword } from '../vault/format'
import { workspaceVaultEnabled } from '../vault/lifecycle'
import { useVault } from '../vault/useVault'

export function VaultPage({ operations }: { operations: Operations }) {
  const identity = operations.identity
  const device = identity.status === 'ready' ? identity.data.device : null
  return <>
    <PageHeader title="Local encryption vault" detail="Manage this browser’s local encryption key." />
    {!workspaceVaultEnabled ? <Notice kind="info">The local vault is unavailable in this build.</Notice>
      : identity.status === 'loading' ? <Notice kind="loading">Checking device approval…</Notice>
      : device?.status !== 'APPROVED' ? <Notice kind="info">Sign in from an approved device to use the vault.</Notice>
      : <VaultPanel key={device.id} deviceId={device.id} />}
  </>
}

function VaultPanel({ deviceId }: { deviceId: string }) {
  const vault = useVault(deviceId)
  const [formError, setFormError] = useState('')
  const passwordInput = useRef<HTMLInputElement>(null)
  const confirmationInput = useRef<HTMLInputElement>(null)
  const setup = vault.status === 'not-initialized'

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const password = passwordInput.current?.value ?? ''
    const confirmation = confirmationInput.current?.value
    form.reset()
    setFormError('')
    if (setup && ([...password].length < minimumPassword || password !== confirmation)) {
      setFormError('Use a matching encryption passphrase with at least 15 characters.')
      return
    }
    void vault.submit(setup ? 'setup' : 'unlock', new TextEncoder().encode(password))
  }

  return <Panel icon={<KeyRound size={18} />} title="Device vault" detail="Your encryption password stays on this device.">
    {vault.status === 'checking' && <Notice kind="loading">Checking device vault…</Notice>}
    {vault.status === 'unlocking' && <Notice kind="loading">Opening vault locally… <Button onClick={vault.lock}>Cancel</Button></Notice>}
    {vault.status === 'error' && <Notice kind="error" onRetry={vault.lock}>This browser could not open secure vault storage.</Notice>}
    {vault.status === 'recovery-required' && <Notice kind="error">Recovery required. This vault format or record cannot be opened. The stored record has been preserved.</Notice>}
    {vault.status === 'unlocked' && <>
      <Notice kind="info">Vault unlocked on this tab.</Notice>
      <Button onClick={vault.lock}>Lock vault</Button>
    </>}
    {(setup || vault.status === 'locked') && <>
      <Notice kind="info">{setup ? 'Vault not initialized.' : 'Vault locked.'}</Notice>
      {setup && <p className="muted">Choose a passphrase of at least 15 characters, separate from your account password. Keep it somewhere safe. It cannot be reset for this vault.</p>}
      {(formError || vault.message) && <Notice kind="error">{formError || vault.message}</Notice>}
      <form className="device-form" autoComplete="off" onSubmit={submit} key={setup ? 'setup' : 'unlock'}>
        <label>Encryption password
          <input ref={passwordInput} type="password" autoComplete="off" maxLength={maximumPassword} required />
        </label>
        {setup && <label>Confirm encryption password
          <input ref={confirmationInput} type="password" autoComplete="off" maxLength={maximumPassword} required />
        </label>}
        <Button variant="primary" type="submit">{setup ? 'Create vault' : 'Unlock vault'}</Button>
      </form>
    </>}
  </Panel>
}
