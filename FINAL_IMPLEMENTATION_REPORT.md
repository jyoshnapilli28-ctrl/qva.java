# QVA 2.0 Final Implementation Report

- Published implementation: `a5a79658296ea73f7828aa593d45a52de8ef8395`
- Branch: `feature/qva-2.0`
- Verification date: 16 November 2026
- Baseline: `45bf472`

## Released changes

- The React workspace under `/app/` and the classic V1 interface at `/` both ship in the Spring Boot JAR. Signed-out invitees can open `/app/register` directly.
- Flyway V8 adds invitation and audit records. HOST administrators can create and revoke expiring, single-use invitations. The database stores token hashes. Registration creates a MEMBER account. Device enrollment and HOST approval remain separate.
- The classic REST routes and legacy WebSocket handshake enforce device approval. Workspace devices can be pending, approved, or revoked.
- The Windows launcher binds Spring Boot to loopback. The documented remote access path uses private Tailscale Serve HTTPS.
- V7 stores versioned Ed25519 public device credentials and opaque KeyPackage records. The opt-in local vault uses a separate P-256 key with PBKDF2-HMAC-SHA-256 and AES-256-GCM.
- The production messaging relay defaults to disabled. The workspace labels fixtures as synthetic. MLS and end-to-end encryption remain unavailable.

## Release verification

A fresh run from the published source verified Java 17 compilation and the executable Maven build. Surefire reported **78 passed, 0 failures, 0 errors, 0 skipped**. The frontend passed **10 component tests**, and the standard and opt-in vault builds succeeded.

The packaged JAR ran against disposable SQLite databases. The checks covered a fresh V1–V8 Flyway migration, a V7-to-V8 upgrade that preserved a fixture account, signed-out registration, HOST and MEMBER permissions, invitation expiry and revocation, device approval and revocation, and persistence across restart. The concurrent redemption case ran in the backend integration suite. See [Final test results](FINAL_TEST_RESULTS.md) for the command, suite counts, and scope of each check.

## Open verification and product limits

- Chromium launch is blocked in the Linux workspace. The packaged HTTP check and vault component tests do not exercise the interactive browser session or vault lifecycle. Browser session expiry, logout, locking, multi-tab behavior, and restart remain unverified in a packaged browser.
- PBKDF2 remains the provisional vault KDF. A lost vault passphrase cannot be recovered. The vault's local P-256 identity and the V7 Ed25519 registry are separate. User confirmation is not cryptographic proof, and the opaque KeyPackage records are not validated MLS packages.
- Device revocation blocks future authorization. It cannot recall content or keys already copied. HOST member suspension is not implemented.
- Windows startup, private Tailscale access, real external-member onboarding, multiple physical devices, production backup/restore, and reboot recovery remain unverified.

This follow-up updates the two release reports. The source under test is the published implementation above. All application runs used disposable databases, and no deployment or production migration was performed.
