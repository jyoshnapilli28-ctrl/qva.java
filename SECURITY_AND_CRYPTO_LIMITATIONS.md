# Security and Cryptographic Limitations

## Application and network boundary

- The Spring Boot application binds to loopback. Private remote access is intended through Tailscale Serve HTTPS only.
- Do not enable Tailscale Funnel, public Cloudflare Tunnel, router port forwarding or a public reverse proxy.
- Tailscale machine sharing, tailnet ACL/grants, Windows Defender Firewall, QVA credentials, QVA device approval and conversation membership are distinct controls. All must be reviewed.
- Existing HOST grants remain database-backed. This freeze adds no HOST grant endpoint and changes no production credentials or grants.
- Session cookies are HttpOnly and SameSite=Lax; the production profile sets Secure. External member access must use HTTPS.
- Classic REST (`/api/rooms/**`, `/api/users/**`) and legacy `/ws/**` now require an approved QVA device as well as an authenticated session. This gate was added in the final freeze and its backend integration test is unexecuted here.

## M6.3 local vault

- The opt-in browser vault creates a local ECDSA P-256 key, wraps the private bytes with AES-256-GCM, and stores the ciphertext plus format metadata in IndexedDB. The public key is packaged alongside the encrypted private bytes.
- PBKDF2-HMAC-SHA-256 at 600,000 iterations and a 16-byte random salt derive an AES-256-GCM wrapping key. The 12-byte nonce is randomly generated once for the single immutable record per browser device; setup refuses to overwrite an existing record, so this implementation does not reuse a nonce for an updated record. Any future write/update format must explicitly design nonce uniqueness.
- The password stays in browser memory and is transferred to a dedicated Worker. The vault password and derived wrapping key are not sent to Spring Boot. Worker termination, typed-array clearing and non-extractable CryptoKeys reduce exposure but cannot guarantee physical memory erasure in a browser.
- The vault is opt-in. It is local to the browser profile and is not the V7 Ed25519 device identity. No proof-of-possession or cryptographic binding between them exists.
- PBKDF2 is provisional. There is no recovery/reset if the vault passphrase is lost. Recovery requires preserving a separately reviewed encrypted backup, which is not implemented by this vault UI.
- Source/component coverage exists for wrong-password retry and malformed/tampered records. Packaged browser lifecycle, logout/expiry, multi-tab, restart and Windows checks were not completed in this environment.

## V7 device identity and MLS

- V7 stores versioned Ed25519 public credentials, fingerprints, user-confirmed verification state and opaque KeyPackage bytes/claims. User confirmation is not cryptographic verification. The service does not prove possession of the matching signing private key or validate the content as a real MLS KeyPackage.
- The local vault's P-256 identity and V7 Ed25519 registry are separate and must not be represented as bound.
- No verified MLS/OpenMLS integration is present. No real MLS credential/group state, message encryption/decryption, membership-removal rekey or MLS identity verification is in place.
- Messaging defaults disabled (`qva.messaging.enabled=false`). UI fixture envelopes are explicitly synthetic test payloads, not cryptographic ciphertext. Do not claim E2EE.
- Device revocation blocks future QVA authorization, but cannot erase messages or keys already copied. If MLS is introduced, protected messaging must remain suspended until removal and rekey are verified.

## Account invitations

- Tokens are 256-bit random values; only their SHA-256 hashes are persisted. Redemption uses a conditional database update and runs transactionally with account creation and audit insertion. New accounts default to MEMBER and device approval remains independent.
- Rate limiting is process-local, so it resets on restart and is not a distributed/shared limiter. Backend integration tests were not run here.

## Logs and data

No production database, password, invitation token, private key or actual member session was used in validation. SQLite contains sensitive account and operational metadata; protect backups and validate restores on a disposable copy.
