# QVA 2.0 private messaging threat model

**M6.1 design review · 15 November 2026**

## Scope and assets

QVA V1 chat runs at the root route and the workspace runs under /app/. The M5.2 relay defaults to disabled. Its synthetic envelopes exercise the transport. The protections below apply to the proposed MLS path, where recipients verify and decrypt locally. Legacy chat is outside the E2EE claim.

Protect message content, device signing and HPKE private keys, MLS state, encryption passwords, vault keys and session credentials. Treat connection times, account and device identifiers, membership, public keys, envelope sizes, receipts and access patterns as sensitive metadata.

| Boundary | Responsibility |
| --- | --- |
| Windows endpoint and trusted client | Hold the encryption password and private state, verify peer bindings and group roster, and decrypt and render locally. |
| Independent installer and update signer | Authenticate code at installation and updates, control access to signing keys, and prevent downgrade. |
| Spring Boot and SQLite on HOST | Authenticate accounts, approve devices, authorize routing, retain opaque frames, order records and report receipts. HOST authority grants administrative operations. |
| Tailnet and HTTPS | Limit reachability and protect the client connection to the authenticated server. Require a private HTTPS secure context outside local testing. |
| Peer verification channel | Bind keys to accounts and devices independently of the HOST directory. |

## Threats and controls

Priority is potential impact before the proposed controls are applied.

| Threat | Priority | Required control and residual risk |
| --- | --- | --- |
| Network interception or unauthorized network device | High | Limit tailnet policy, validate HTTPS certificates and secure contexts, and verify MLS messages at endpoints. Network observers may see connection timing and endpoints. |
| Database copy or stolen backup | High | Keep message frames opaque; use bounded retention and password-encrypted vault blobs if backed up. A copy exposes metadata and account password hashes and can enable offline vault guessing. A writer can tamper with service state. |
| Malicious relay or compromised server | Critical | Verify credentials, group, state transitions and messages locally. Pin bindings and preserve monotonic client state. A relay can still collect metadata, falsify its own operational observations, censor, delay, or present divergent views. |
| HOST-controlled directory or compromised Authentication Service | Critical | Independently compare full fingerprints and pin bindings. Require explicit trust on every key change and validate the resulting MLS roster. HOST operations confer no automatic leaf. A malicious directory can prevent onboarding or delivery. |
| HOST replaces JavaScript | Critical | The current JAR-hosted client shares HOST trust and can be changed to capture passwords, keys and plaintext. Independently verified client installation is required for this claim. HTTPS, CSP and SRI controlled by the same HOST cannot establish independence. |
| Malicious update or dependency | Critical | Separate signing/release authority from runtime HOST. Verify provenance, pin versions, restrict pipeline and signer access, and prevent downgrades. A compromised signer or build pipeline remains able to ship hostile code. |
| Stolen account credentials, live session or device cookie | High | Preserve the one-active-login policy. Revalidate authentication, ownership and approval on sensitive operations. Use HTTPS, secure cookies and CSRF controls. Revoke relevant sessions and disconnect affected operations sockets. Stolen server credentials can disrupt service and confer no MLS key without separate access to trusted client state. |
| Malicious or compromised MEMBER | Critical | Keep identity per device, remove compromised leaves and verify replacement identity. An authorized endpoint can retain what it sees, and an ongoing active endpoint compromise can disclose new plaintext. |
| Vault extraction or password guessing | Critical | Use a separate local password, reviewed memory-hard KDF, random salt, authenticated encryption and bounded metadata. Keep unlocked state in memory only until lock and clear references or stop workers, with no guarantee of JavaScript/Wasm memory erasure. Server throttling does not stop offline guesses. |
| Malicious JavaScript or XSS | Critical | Use packaged local assets, restrictive CSP, safe text rendering, bounded binary parsing and no third-party scripts in encryption views. Exclude secrets from logs and telemetry. Worker isolation cannot protect against hostile trusted UI code or a compromised OS. |
| Message replay, tampering or cross-conversation use | High | Verify MLS group, epoch, authenticated sender and replay state. Bind QVA application context to the expected conversation. Persist validated cursors and ratchet state atomically. Keep REST idempotency for retries. Failed verification yields no delivery or read receipt. |
| Participant or device revocation, membership drift and racing commits | Critical | Immediately remove application access. Suspend protected sends until authenticated, verified removal advances the MLS epoch. Remaining devices check the actual roster and expected state. Previously copied keys and plaintext cannot be recalled. |
| Partial delivery, offline gaps, backup restore or rollback | High | Use durable ordered controls, transactional mailbox/outbox, monotonic state and explicit recovery if retention creates a gap. Fresh clients without locally trusted state need independent verification. |
| Resource exhaustion | High | Bound frames, state, request rates, queues and retries. Avoid collecting message content for metrics. HOST, tailnet and endpoints can still deny service. |

## Security claims by attacker

- **Network attacker.** Private HTTPS protects transport. Verified MLS peers protect content end to end.
- **Database attacker.** A data copy exposes metadata and ciphertext. Weak passwords can expose encrypted vault records through offline guessing. Write access can manipulate authorization and delivery.
- **Malicious server with independently trusted clients.** Device pinning, verified roster transitions and local state constrain impersonation and rollback. The server sees routing metadata and can suppress traffic. The initial design has no general fork-detection guarantee across offline clients.
- **Malicious HOST with control of client code or signer.** The HOST can read secrets when hostile code runs. The current JAR-served browser code cannot support this resistance claim.
- **Compromised client or malicious group member.** The client can disclose messages available to it. Verified removal, fresh epochs and trustworthy remaining devices restrict future exposure.

Forward secrecy depends on erasing prior keys. A readable local history or old backups increase exposure after later compromise. Post-compromise security requires a fresh honest update after the compromise ends. Newly enrolled devices join future epochs; past message recovery requires an explicit approved backup or transfer policy. Loss of the encryption password or unrecoverable keys can mean permanent loss of protected history.

## Verification gates

1. Inspect synthetic test requests, storage and logs for plaintext and private state leakage using disposable identities and databases.
2. Verify password failure, lock, refresh, expiry, restart, offline recovery, malformed or wrong-key data, duplicate/reordered delivery and recovery failures.
3. Verify server-side revocation and proof that removed leaves cannot decrypt after an authorized remove, including offline and racing-commit scenarios.
4. Verify independent client installation, update trust and rollback behavior before claiming resistance to modified HOST code.
5. Record packaged-JAR, legacy V1, Windows runtime and four physical-device results independently.
