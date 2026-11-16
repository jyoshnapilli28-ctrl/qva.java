import { useCallback, useEffect, useState } from 'react'
import { getSession } from '../services/auth'
import { ApiError, isUnauthenticated } from '../services/http'
import type { Session } from '../types'
import { lockWorkspaceVault, workspaceVaultEnabled } from '../vault/lifecycle'

type State =
  | { status: 'checking' }
  | { status: 'signed-out'; expired?: boolean }
  | { status: 'unavailable' }
  | { status: 'signed-in'; session: Session }

export function useSession() {
  const [state, setState] = useState<State>({ status: 'checking' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    getSession(controller.signal).then(session => {
      if (!controller.signal.aborted) setState({ status: 'signed-in', session })
    }).catch(error => {
      if (controller.signal.aborted) return
      if (isUnauthenticated(error)) {
        setState({ status: 'signed-out', expired: error instanceof ApiError && error.expired })
      } else {
        setState({ status: 'unavailable' })
      }
    })
    return () => controller.abort()
  }, [attempt])

  const retry = useCallback(() => {
    setState({ status: 'checking' })
    setAttempt(value => value + 1)
  }, [])
  const signedIn = useCallback((session: Session) => setState({ status: 'signed-in', session }), [])
  const signedOut = useCallback(() => setState({ status: 'signed-out' }), [])
  const expired = useCallback(() => {
    if (workspaceVaultEnabled) lockWorkspaceVault()
    setState(current => current.status === 'signed-in' ? { status: 'signed-out', expired: true } : current)
  }, [])

  return { state, retry, signedIn, signedOut, expired }
}
