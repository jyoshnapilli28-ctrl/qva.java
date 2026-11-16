# QVA workspace operations

QVA v1 remains at `/`. The packaged React workspace starts at `/app/`. An approved HOST sees the administrative dashboard at `/app/host`. Members use Overview for their account and current browser, Members for the existing roster, and Network for the relay and their own approved browser.

## Architecture

- `OperationsAccess` resolves an active database account from the authenticated principal. Only a grant for that immutable database ID yields HOST. No grant yields MEMBER. The singleton table enforces one grant and the read path disables HOST access if it ever observes multiple rows.
- `WorkspaceSessions` ties an observed Spring session to the authenticated account and one device. Dashboard session IDs are separate opaque references. Existing one-session-per-account behavior remains in effect. One account can register several browsers and use them in separate login sessions.
- Device registration issues a random server ID and a separate random browser credential. Only a SHA-256 digest of that credential is stored. The HttpOnly, SameSite cookie is scoped to the v2 APIs and uses Secure on HTTPS. The credential is checked with the authenticated user's ID. A browser profile identifies a QVA registration. It does not attest to physical hardware.
- A newly granted HOST can self-enroll their first browser. Subsequent browsers require approval from an already approved host browser. An operator who loses all approved HOST browsers needs a separately reviewed recovery procedure. Registration, approval, and revocation persist. Session and recent activity state reset when the process stops.
- `/api/v2/live` is a dedicated same-origin WebSocket. The handshake and live events require an authenticated session and an approved owned device. The server checks session and device state during message handling, broadcast, and periodic cleanup. The existing STOMP and SockJS chat endpoints are separate.
- Browsers send a heartbeat every 10 seconds. The server treats 35 seconds without a heartbeat as stale and sweeps every two seconds. Several tabs for one approved device count as one connected device. A device is disconnected when its last authorized socket closes or expires. Client reconnect uses exponential backoff and reconciles REST snapshots. VPN state remains Unknown.
- Application-connected means the v2 presence socket is current. Authentication and device approval are independent. The classic roster measures classic chat presence separately.

## API contract

All v2 JSON endpoints require an authenticated Spring session. Mutation requests also need the session CSRF token returned by `GET /api/v2/csrf` in the header named by that response. Cross-origin configuration for v1 is scoped to the legacy routes. The new endpoints accept same-origin clients.

| Method and path | Behavior |
| --- | --- |
| GET `/api/v2/identity` | Own role, own current device, public session reference and first-host enrollment eligibility. |
| GET `/api/v2/csrf` | Session CSRF token and header name. |
| POST `/api/v2/devices` | Register current browser with JSON `{"name":"label"}`. Issues a device cookie. New registrations are pending. |
| POST `/api/v2/devices/bootstrap-host` | A granted HOST bootstraps their first enrolled browser. The database operation checks that no device has been approved for that grant holder before. |
| GET `/api/v2/network` | HOST with an approved browser sees approved device states. A MEMBER sees only their current approved browser. |
| GET `/api/v2/host/dashboard` | Approved HOST only. Active accounts, device inventory, observed sessions, and recent activity. |
| POST `/api/v2/host/devices/{id}/approve` | Approved HOST only. Approves a pending registration. |
| POST `/api/v2/host/devices/{id}/revoke` | Approved HOST only. Revokes the credential, its sessions, and its sockets. To revoke the currently used HOST browser, sign in on another approved HOST browser. |
| POST `/api/v2/host/sessions/{id}/terminate` | Approved HOST only. Terminates the observed session. Use Sign out for your own session. |
| WS `/api/v2/live` | Approved device and current session required. The sole client message is the text `heartbeat`. The server sends scoped events. |

The event reports ID, server timestamp, type, observation source, user ID and device ID where applicable. An approved HOST sees operational events. Other sockets see events for their own device. No application WebSocket destinations, public topic subscriptions, arbitrary writes, or token exposure are part of this protocol. Unknown or invalid frames close the socket.

## Migration and provisioning

V4 adds the singleton grant table, audit table, devices table and owner index. It makes no changes to V1–V3 or to existing tables and seeds. The operator command reads and writes an existing database explicitly selected by path. Its transaction records the action, OS operator identity, timestamp, and affected database ID. There is no provision/grant HTTP path. Role changes invalidate observed administrative sessions and close their sockets. Access checks read current grants.

Build and start against a disposable database first. Use Python 3 and, with the server stopped after migration, run the appropriate command only for an approved ID and database:

```sh
python tools/qva-host.py --database <existing-test-database> grant <approved-user-id>
python tools/qva-host.py --database <existing-test-database> revoke <approved-user-id>
```

Write access to the database directory is administrative authority. Restrict it to the server identity and trusted operators. Retain the existing v1 JAR and create and verify a consistent SQLite backup before any approved production migration. Preserve V4 state by keeping an upgraded backup as well. Restore only under an approved recovery procedure. Never downgrade in place by deleting Flyway records.

## Security review

- HOST checks run through Spring Security on host routes and are repeated by protected service operations. Browser role rendering is informational.
- v2 mutations require CSRF. Session registration and WebSocket upgrades verify server-side account, device, and approval. Invalid sessions, pending credentials, and revoked credentials cannot open the v2 presence socket.
- Revocation cannot erase prior downloads. A browser that deletes its cookie can submit a new pending registration. It cannot regain the revoked approval. Legacy v1 remains account-authenticated and does not adopt v2 device approval.
- The device credential is a bearer secret. Use trusted HTTPS with private network access and keep browser profiles protected. Proxy/TLS behavior and the intended Windows/Tailscale environment must be checked before rollout.
- Connections and activity are bounded in process. Device registration caps non-revoked records per account. Operational history is a bounded in-memory view. Only explicit HOST grant changes have a durable provisioning audit.
- This release provides operations visibility. It leaves E2EE, advanced analytics, physical-device attestation, and independent VPN observation outside the scope.

## Four-device acceptance

After separate permission to deploy and provision, prepare a verified backup and install the tested build on the host. Use the four intended Windows browsers with the private transport. Verify sign-in, approve each device, and confirm host-only actions are blocked for members. Open the topology, disconnect and reconnect each client independently, and confirm that another connected browser stays online. Leave one connection idle long enough to exercise the stale timeout. Restart the host and confirm that persisted approvals remain while sockets reconnect. Record operating system, timestamps, build, observations and rollback evidence. Only mark Windows or physical-device acceptance after these checks are run on the actual laptops.
