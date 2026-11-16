# QVA 2.0 Final Implementation Freeze

- Date: 2026-11-16
- Branch: `feature/qva-2.0`
- Baseline: `45bf472 chore: establish QVA v1 source baseline`

## Summary

The working tree contains the previously developed QVA 2.0 M2.1–M6.3 source changes. This freeze audited those changes, retained the local vault and V7 device identity as separate systems, and added a minimal HOST-issued invitation workflow. The production messaging relay remains disabled by default. QVA does not currently provide MLS or end-to-end encryption.

## Changes in this freeze

- Added additive SQLite migration V8 for single-use invitations and invitation audit events. V1–V7 were not edited.
- Added HOST-only invitation creation, listing, revocation and audit endpoints, plus anonymous CSRF-protected member registration, hashed tokens, expiration, one-use redemption, username validation and process-local throttling.
- Added the registration page and HOST invitation manager. New accounts are ordinary MEMBER accounts and have no device record; the normal device-registration and HOST-approval step remains separate.
- Removed broad MVC CORS configuration and old Cloudflare/direct-IP WebSocket origins.
- Changed server launchers to bind to loopback and use private Tailscale Serve for remote HTTPS. Updated client configuration and package instructions to remove the public-tunnel path.
- Closed the classic REST and legacy WebSocket authorization bypass: `/api/rooms/**`, `/api/users/**` and `/ws/**` now require the same approved QVA device as the workspace. Device cookies use the root path, and `/api/v2/identity` renews earlier v2-scoped cookies for compatibility.
- Added backend integration tests for invitation lifecycle and the classic route's pending/approved/revoked-device boundary. These tests are present but could not run here.
- Added the final deployment, onboarding, invitation, security, test and release documents listed below.
- Fixed a TypeScript syntax error in the invitation manager and wrapped an existing Link-based component test in a MemoryRouter.

## Components and boundaries already present

- V1 classic root UI and a React workspace under `/app/`.
- Database-backed HOST grant checks, MEMBER default role, device registration and approval/revocation.
- V5–V6 conversation lifecycle and opaque-envelope relay code. `qva.messaging.enabled` defaults to false; the workspace labels its local fixtures as synthetic.
- V7 versioned Ed25519 device public-identity registry, conversation-scoped lookup, user-confirmed verification state, and single-use KeyPackage claim storage. KeyPackage bytes remain opaque/synthetic and are not validated MLS packages.
- M6.3 local opt-in vault using browser P-256, PBKDF2-HMAC-SHA-256 at 600,000 iterations, AES-256-GCM wrapping, IndexedDB, and a dedicated Worker. The P-256 key is not bound to the V7 Ed25519 identity.

## Verification summary

- Frontend component tests: PASS, 10 tests across 3 files.
- Frontend standard production build: PASS.
- Frontend vault-enabled production build: PASS.
- Raw SQL migration smoke test: PASS for V1–V8 on a disposable SQLite database, including a close/reopen persistence check. This is not a Flyway or Spring integration test.
- Backend unit/integration tests and Java compilation: BLOCKED. The environment has Java 21 runtime only, no `javac`, no Java 17 toolchain and no Maven.
- Playwright suite: BLOCKED for full application regression. Without a packaged backend/base URL it reported 1 setup failure (`QVA_TEST_PASSWORD` missing), 6 skipped and 3 not run. The retained packaged vault lifecycle test did not run. A previous packaged-browser attempt was blocked at Chromium startup by the environment's process/socket restrictions.
- Windows, Tailscale sharing, Firewall, host/member login, real two-device messaging, and reboot/restore checks: NOT RUN.

See [FINAL_TEST_RESULTS.md](FINAL_TEST_RESULTS.md) for commands, exit statuses and detail.

## Security and release limitations

- No MLS runtime or real encrypted messaging is integrated. Do not call fixture envelopes ciphertext or claim E2EE.
- PBKDF2 is retained as the provisional vault KDF; no Argon2 migration is included. Vault password recovery is not available.
- Browser lifecycle, lock erasure, session-expiry/logout lock behaviour and nonce/tamper behavior have source/component-level checks but not a packaged-browser lifecycle pass in this environment.
- Invitation backend code and V8 were not compiled or exercised through Spring because Maven/JDK compiler were unavailable. The new invitation integration test is present but unexecuted. Treat invitation support as unverified until the release environment passes it.
- HOST member suspension is not implemented. HOST can inspect members, manage invitations, approve/revoke devices and inspect existing operational events. Device revocation blocks future QVA authorization, but cannot erase copied messages or keys.
- Approved-device gating of the retained classic REST/WebSocket paths is a new backend security change and requires the added Spring integration test to pass before deployment.
- Tailscale machine sharing is not itself QVA registration or device approval. The host must review existing tailnet ACL/grants and test one external account. No tailnet policy was modified here.
- No production database was opened, migrated or modified; no credentials or HOST grants were changed.

## Exact source and documentation changes

The authoritative changed-file list is included in `FINAL_TEST_RESULTS.md` and the checkpoint archive. The repository working tree remains uncommitted. No commit, push or deployment was performed.
