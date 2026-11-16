# M6.2 device identity foundation

The development API is behind qva.device-identity.enabled, which defaults to false. Its flag is separate from qva.messaging.enabled. V7 adds tables, indexes, and read-only views. It does not change V1–V6, existing accounts, device records, conversation state, or the message transport. A production startup of a JAR containing V7 would migrate the configured database even with both feature flags disabled. Validate and approve a future production migration separately. Take a consistent pre-upgrade SQLite backup. An approved rollback should restore that backup to a separate path and verify the previous JAR against it before switching. Do not drop V7 tables in place or reissue packages from restored state.

## API

All routes are relative to /api/v2/device-identities. Authentication, a live session bound to the authenticated account/device, and an approved device cookie are required. The existing /api/v2 CSRF policy protects mutations. Responses use no-store caching. Body sizes below refer to bytes.

| Method and route | Input | Behavior |
| --- | --- | --- |
| GET /me | None | Return the caller's current credential or 404. |
| PUT /me/credentials/{expectedVersion} | application/octet-stream with a canonical 44-byte Ed25519 SubjectPublicKeyInfo public key | Register with expected version 0 or rotate from the current version. Return 201 with the new version. Conflicting, duplicate and stale writes return 409. |
| GET /conversations/{conversation}/devices/{peer} | Conversation and device UUIDs | Return the peer's current public identity if both devices are approved and both accounts belong to the conversation. An unavailable peer or conversation returns 404. |
| PUT /conversations/{conversation}/devices/{peer}/verification/{version} | QVA-Identity-Fingerprint header, 64 lower-case hex characters | Record a caller's explicit confirmation of the current peer version and fingerprint. This requires a current caller identity and is idempotent for the exact two versions. |
| POST /me/credentials/{version}/key-packages | application/octet-stream with 1–16,384 public KeyPackage bytes | Store a fixture or serialized candidate package for the caller's current version. Keep up to 32 unexpired, unclaimed records per credential. Return 201 with package ID and expiry. |
| POST /conversations/{conversation}/devices/{peer}/key-packages/{version}/claim | UUID Idempotency-Key header | Claim one unexpired package from the peer's current verified version. A replay or unavailable package returns 409. An out-of-scope peer returns 404. |

Identity responses use standard JSON Base64 for the DER public key and lower-case SHA-256 hex over those exact DER bytes for the fingerprint. Both user ID and device ID come from stored ownership. Returned credentials are CURRENT. Historical rows remain in SQLite for version integrity. SELF describes the caller's own identity, UNVERIFIED is the default peer state, KEY_CHANGED identifies a previously verified peer that rotated, and VERIFIED indicates an attestation from the calling device's current credential version. Rotation of the verifying credentials also resets that device's confirmations. Account ID, device ID, credential version and fingerprint should appear together during future independently conducted verification.

## Transaction and access behavior

The claim is a SQLite UPDATE with a RETURNING result, inside a transaction. Its predicate checks the current credentials, peer confirmation, membership, active accounts and approved devices in that write. The same transaction clears the stored package body and retains claim and digest metadata so neither the package nor claim ID can be replayed. If delivery of a successful claim's HTTP response fails, the package is spent. The same claim ID retrieves no bytes and does not burn a second package. Expiry is seven days. Rotation scrubs old package bodies. A successful later publication scrubs expired bodies for that device.

Direct and group access is restricted to both current account members. HOST has no visibility bypass. An approved same-account sibling device still needs its own session/device binding and its own attestation, and the account must belong to the conversation. The existing one-session-per-account policy remains in effect. Device revocation removes eligibility immediately at the authorization layer. A response already authorized and sent cannot be recalled. Future cryptographic group removal requires a verified MLS epoch transition.

## Security limitations and next work

- This service validates the Ed25519 public key encoding and binds an upload to the authenticated workspace device. It does not yet prove possession of the signing private key or bind a package's embedded leaf and signature to that key. Treat packet bytes as synthetic and opaque. Real MLS clients must validate package encodings, signatures, suite, lifetime, key confirmation and independent account/device key pins before use.
- A stored VERIFIED row documents a user's declaration. The server cannot verify that an out-of-band comparison took place. Local pinning, first-contact UI, changed-key confirmation and independent client distribution remain required. The HOST can alter server assertions and metadata.
- Claimed package rows keep digests to prevent reuse. Expired payloads for inactive devices can remain on disk, and retained metadata grows with publication churn. Use bounded cleanup, explicit replenishment, and abuse controls before production. Refer to the threat model for SQLite backup and rollback risk.
- Recovery and rotations do not transfer the previous signing key, revalidate existing conversations, or establish future MLS epochs. M6.3 needs a reviewed local KDF, encrypted IndexedDB vault, setup/unlock/lock flows, an atomic outbox and a documented recovery policy. M6.4 and M6.5 add direct and group MLS encryption after the implementation gates clear.

## Reproduce packaged verification

Build with Java 17 and dependencies available to Maven and npm. Use an accepted V6 packaged JAR, and run the restart script against the new packaged JAR. It creates and removes a private disposable SQLite directory, uses only loopback, and generates test identities and credentials locally each run.

    mvn -Pqva-web package
    python tools/test-device-identity-restart.py --previous-jar /path/to/v6.jar --candidate-jar target/private-chat-server.jar

Two inherited test cases use a fixed release database path. For a full regression run, provide a disposable fixture there in an isolated copy, never a production database.
