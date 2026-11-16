# QVA v1 session API used by the React frontend

| Request | Existing behavior |
| --- | --- |
| GET /api/auth/me | 200 JSON with `username` and `authenticated: true` for a valid session. 401 for an unauthenticated request. |
| POST /api/auth/login | Form fields `username` and `password`, encoded as `application/x-www-form-urlencoded`. 200 JSON on success, 401 on credential rejection. Spring Security creates or rotates the HTTP session. |
| POST /api/auth/logout | Invalidates the HTTP session, clears JSESSIONID and returns 200 JSON. |
| GET /api/auth/status | Public service availability response. It does not check session identity. |

The frontend uses same-origin requests through the Vite development proxy and lets the browser manage the HttpOnly session cookie. It probes /me on startup and after login. It does not persist credentials or infer administrator permissions from a username. Current accounts share the USER role and Spring Security limits each account to one HTTP session. A new login can expire an earlier session. The current API excludes these routes from CSRF checks. This milestone makes no changes to Spring Security.

Source: SecurityConfig.java, AuthController.java, UserDetailsServiceImpl.java and application.properties at baseline 45bf472.

## Session expiry in the React workspace

During use, a 401 on a workspace API returns the user to sign-in and preserves the requested path. With the existing one-session policy, Spring Security 6.2 may send its default concurrent-session expiry text with HTTP 200. The React HTTP service recognizes that specific response as expiry. A generic API failure displays a retry state. The Spring Security session configuration stays the same.
