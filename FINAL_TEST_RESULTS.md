# QVA 2.0 Final Test Results

- **Source verified** `a5a79658296ea73f7828aa593d45a52de8ef8395` on `feature/qva-2.0`
- **Reverification date** 16 November 2026

The release freeze originally recorded a missing compiler and Maven. The original 78-test run's temporary files were unavailable for this review, and the older reports in the checkout did not contain the invitation suite. This report uses a fresh build from a Git archive of the published commit in a separate directory. All database and account fixtures were disposable.

## Build and automated tests

- Linux with Eclipse Temurin **17.0.21**, Maven **3.9.9**, Node **24.22.1**, and npm **11.9.0**.
- `mvn -Pqva-web -DargLine="-javaagent:<Byte Buddy agent 1.14.13>" clean verify` passed, including Java compilation, the standard React build, and Spring Boot repackaging. The test JVM preloaded the agent because this workspace blocks Mockito's self-attachment.
- Surefire reported **78 tests run, 0 failures, 0 errors, 0 skipped**. The XML reports independently agree with the Maven summary.
- `npm run test:components` passed **10 tests in 3 files**. The opt-in build with `VITE_QVA_LOCAL_VAULT=1` also passed. Both builds compile the frontend.
- Rebuilt `target/private-chat-server.jar`: 66,734,420 bytes. SHA-256: `e92b4b4361f8f4405cd63829cf750791c64b4227eb707e1a6cb11a2314a605fa`. The archive contains both the classic and React HTML entry points. The checksum identifies this reverification build.

## Surefire suite counts

| Suite | Passed |
|---|---:|
| `AuthAuditTest` | 1 |
| `AuthenticationRegressionTest` | 10 |
| `BackupRestoreTest` | 1 |
| `CustomAccountsMigrationTest` | 11 |
| `DeviceApprovalGateIntegrationTest` | 1 |
| `DeviceIdentityIntegrationTest` | 6 |
| `InvitationIntegrationTest` | 2 |
| `MessagingIntegrationTest` | 1 |
| `PrivateChatIntegrationTest` | 16 |
| `RestApiTest` | 17 |
| `RestartPersistenceTest` | 1 |
| `StaticAssetTest` | 3 |
| `WebSocketSecurityTest` | 8 |
| **Total** | **78** |

The invitation concurrency test launched two registrations against one token. It asserted one HTTP 201, one HTTP 409, and one new user row. The device approval gate test exercised the classic REST authorization and legacy WebSocket handshake gate with pending, approved, and revoked device states. The approved MockMvc request reached Spring's handshake and got HTTP 426 because it omitted the WebSocket version.

## Packaged integration checks

These were direct HTTP and database checks against the executable JAR. The application ran on loopback with disposable credentials and SQLite files.

| Check | Verified result |
|---|---|
| Fresh Flyway migration | Production-profile startup applied V1–V8 in order, verified against `flyway_schema_history`. |
| V7-to-V8 upgrade | Initialized a separate database with Flyway target V7, changed a fixture account's display name, then started with the regular target. V8 applied, the fixture change survived, and the new invitation table was empty. |
| Packaged routes and authorization | Signed-out GETs to `/` and `/app/register` returned the respective HTML. HOST fixture login succeeded. Anonymous invitation access returned 401 and an approved MEMBER's invitation access returned 403. |
| Invitation lifecycle | HOST created an invitation, valid redemption created a MEMBER, malformed username returned 400, and replay, expiry and revocation returned 409. |
| Devices and persistence | New device started pending, HOST approval enabled the protected REST route, and HOST revocation persisted through a process restart. The member could sign in after restart, with device status REVOKED and protected REST access denied. Account and invitation records survived. |

## Browser and deployment limits

- The packaged Playwright HTTP test passed. The browser session test stopped at Chromium startup with `socket() failed: Operation not permitted (1)` and a `ptrace` permission error. Interactive session behavior remains unverified.
- The packaged vault lifecycle did not run. The vault component tests and opt-in build leave setup/unlock, session expiry, logout, inactivity locking, multi-tab locking, and restart behavior open in a real packaged browser.
- Windows HOST deployment, live Tailscale sharing and firewall policy, real external-member onboarding, multiple physical devices, and production backup/restore were not tested. The production database and credentials were untouched.
