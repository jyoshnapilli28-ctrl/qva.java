export const workspaceVaultEnabled = import.meta.env.VITE_QVA_LOCAL_VAULT === '1'

const configured = Number(import.meta.env.VITE_QVA_VAULT_IDLE_MS ?? 300_000)
export const idleMilliseconds = Number.isSafeInteger(configured) && configured >= 1_000 && configured <= 900_000
  ? configured : 300_000

const lockEvent = 'qva:lock-local-vault'
const channelName = 'qva-local-vault-locks'

export function lockWorkspaceVault() {
  window.dispatchEvent(new Event(lockEvent))
  if (typeof BroadcastChannel === 'function') {
    const channel = new BroadcastChannel(channelName)
    channel.postMessage('lock')
    channel.close()
  }
}

export function onWorkspaceVaultLock(lock: () => void) {
  const channel = typeof BroadcastChannel === 'function' ? new BroadcastChannel(channelName) : undefined
  if (channel) channel.onmessage = ({ data }) => { if (data === 'lock') lock() }
  window.addEventListener(lockEvent, lock)
  return () => {
    window.removeEventListener(lockEvent, lock)
    channel?.close()
  }
}
