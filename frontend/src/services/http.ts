export class ApiError extends Error {
  constructor(public readonly status: number, public readonly expired = false) {
    super(`QVA returned HTTP ${status}`)
  }
}

export function isUnauthenticated(error: unknown) {
  return error instanceof ApiError && error.status === 401
}

// Spring Security 6.2's default concurrent-session handler writes this body with HTTP 200.
const expiredSessionResponse = 'This session has been expired (possibly due to multiple concurrent logins being attempted as the same user).'

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    credentials: 'same-origin',
    cache: 'no-store',
    signal: init?.signal ?? AbortSignal.timeout(8000),
  })
  if (!response.ok) throw new ApiError(response.status)
  if (!response.headers.get('content-type')?.includes('application/json')) {
    if ((await response.text()).trim() === expiredSessionResponse) throw new ApiError(401, true)
    throw new Error('QVA returned an unexpected response format')
  }
  return response.json() as Promise<T>
}
