# QVA 2.0 E2EE protocol decision

**M6.1 proposal · 15 November 2026 · Approval pending**

## Decision

Use IETF Messaging Layer Security (MLS 1.0, RFC 9420) for direct and group messaging. A direct conversation is an MLS group limited to two account principals. Each verified device credential occupies its own leaf. Group conversations allow additional account principals, subject to QVA membership policy and client verification of each added leaf.

Evaluate OpenMLS first in an isolated Rust-to-Wasm browser worker. Spring Boot remains the Authentication and Delivery Service and SQLite retains opaque messages and routing metadata. Both approved account sessions and independent device verification are required. Begin with MLS member commits and Welcome-based joins, with a single tested MLS 1.0 ciphersuite. Test MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519 for interoperability. The evaluation gates below govern selection of the exact version.

## Comparison

| Candidate | Direct, group, device and offline support | Security, maintenance and license | QVA fit |
| --- | --- | --- | --- |
| OpenMLS | Direct and group use one protocol. Device leaves, asynchronous KeyPackages and Welcomes, and epoch updates handle joins, rotation and removal. Offline clients need ordered control history and bounded catch-up. | MIT, active upstream. MLS forward secrecy and post-compromise security depend on update and key-erasure assumptions. SRLabs assessed the OpenMLS core in 2026. Providers, the sample delivery service and client, and QVA integration layers fall outside that scope. | Preferred. React calls a narrow Worker API. Upstream Wasm build coverage differs from native Windows testing. Spring Boot can reuse the existing opaque transport. |
| ts-mls | Browser-native TypeScript MLS. QVA still supplies identity, storage, revocation and offline reconciliation. | MIT. The maintainer discloses single-volunteer maintenance and no professional audit. | Possible synthetic interoperability peer. |
| Wire Core Crypto | Browser Wasm demo and higher-level MLS client. Adds persistence and identity assumptions. | Active upstream. Core package declares GPL-3.0-only. Exact browser artifact audit coverage unverified. | Higher integration and licensing cost. |
| Signal libsignal | Supports asynchronous direct messaging and multi-device session management with Sesame. Group architecture and group membership require separate analysis. Ratchet guarantees depend on the lifecycle. | Active upstream, AGPLv3. Exact QVA browser artifact audit coverage unverified. | Official TypeScript library relies on Node native bindings, and Java uses native bindings. No supported drop-in browser integration was identified. |
| Matrix Rust/WASM | Direct and group rooms, per-device keys and offline sync. Matrix room state and key distribution are integral. Megolm's security and recovery properties differ from Olm's. | Active upstream, Apache-2.0. The published Vodozemac assessment covers Olm and Megolm primitives. Wrapper and release-specific assurance require review. | Browser viable if QVA adopts Matrix service and sync semantics, which would substantially change the existing relay. |
| Bouncy Castle Java MLS | Independent MLS implementation for interoperability. | MIT. Exact Java MLS artifact audit coverage unverified. | Possible CI test peer. End-user private material remains on clients. |
| Web Crypto | Platform primitives suitable for vault AEAD. Messaging lifecycle, membership and asynchronous delivery need an established protocol. | Secure-context API with AES-GCM. The standard API does not provide Argon2id. | Reuse reviewed platform operations for storage where appropriate. |

## Dependency acceptance

- Treat OpenMLS 0.9.2 as the minimum candidate on the 0.9 line as of this review. The August deserialization advisory lists 0.9.0 as patched, the October 8 advisory lists 0.9.1 on that line, and the October 22 advisories list 0.9.2. Confirm publication of the exact crate and matching source tag, all outstanding advisories, crypto and storage providers, transitive versions, licenses and maintenance before use.
- Pin the Rust toolchain, providers and crate checksums. Review the lockfile and SBOM and publish traceable artifact hashes. Test public MLS vectors, an independent MLS implementation, and the exact production worker build on Windows Edge and Chrome or the selected signed client runtime. Verify browser randomness, storage and upgrade behavior. Recheck advisories at release.
- Review the vault KDF independently. Prefer a maintained Argon2id provider. Benchmark RFC 9106's 64 MiB, three-pass, four-lane profile on Windows. Use unique random salts, bounds on imported KDF metadata and reviewed AEAD with unique nonces and authenticated version and binding metadata. Choose the exact parameters from measured evidence.

## Trust and identity

Keep immutable account IDs, server device IDs, device cookies and MLS signing keys separate. Use MLS BasicCredentials with versioned account and device bindings. Their labels are assertions. Establish key trust by independently comparing full fingerprints or verified QR data tied to the account and device. Pin verified bindings locally. Require explicit verification for each new key or device, including after recovery. Validate the actual MLS Add or Remove against the pinned policy. Independent fingerprint verification is feasible for four laptops. TOFU alone leaves initial substitution unresolved, and a HOST-controlled CA or directory inherits the HOST trust boundary.

For the malicious-HOST requirement, evaluate a separately installed Windows client using the same React sources and packaged local assets. An installer and updates need a trust root acquired independently of the runtime HOST, external signing control and downgrade protection. A browser-only alternative requires independently trusted app delivery outside HOST control and separate validation. The existing JAR-served UI can support V1, operations and controlled testing. Windows client distribution is an explicit approval item.

## Integration sequence after approval

| Step | Proposed code, data and interfaces | Acceptance evidence |
| --- | --- | --- |
| M6.2 · Identity | Reuse OperationsAccess, WorkspaceSessions and DeviceStore. Add public credential version/status and KeyPackage endpoints plus public identity and single-use KeyPackage tables in the next Flyway migration, currently V7. Check device ownership, approvals, session and applicable CSRF. Consume KeyPackages transactionally. | Identity persistence, cross-account isolation, explicit key-change verification, package replay and concurrent consumption, and device revocation. |
| M6.3 · Vault | Add a local worker, setup/unlock/lock UX and encrypted IndexedDB state. Provide scoped storage of opaque vault blobs as required, using a versioned format distinct from account credentials. Define whether current state, local history and backups have different retention policies. | Correct and incorrect password, no secret leakage, lock on expiry and refresh, concurrent tabs, tampering and rollback. |
| M6.4 · Direct messages | Extend MessagingController, DirectMessageStore, MessageLifecycle, the frontend messaging service and Messages page. Introduce an explicit MLS envelope format, ordered control records and device-scoped Welcomes. Reuse conversation routes, idempotency and private reconciliation polling. Keep format 1 fixture history separate. | Exchange verified real messages in two synthetic browser profiles, including offline catch-up and restart. Verify ciphertext-only server storage and logs, sender bindings and receipts. |
| M6.5 · Groups | Bind control records to group ID, membership generation and expected epoch. Stage the commit client-side, atomically persist the opaque control record and next epoch on server acceptance, and advance local state after MLS verification. Handle lost responses and racing writers. | New, offline and revoked devices; participant changes; racing commits and crash recovery. Confirm excluded leaves cannot decode post-removal traffic. |
| M6.6 · Verification | Run public vectors, independent peer tests, regression suites, packaged integration and Windows secure-context evaluation using disposable databases. Record dependency and distribution reviews. | Verified leakage, tampering, replay, recovery, expiry, restart and downgrade tests. Production messaging stays disabled pending explicit authorization. |

Persist ratchet state and outbox entries atomically before submission, and lock concurrent local writes. Reconcile control messages in order and advance persisted cursors only after durable validation. Server acceptance, verified delivery and read are separate states. A delivery receipt follows valid local decryption; a read receipt requires local display and read policy. Give control records and Welcomes their own bounded retention and recovery rules. Preserve the current one-active-session policy. Upon removal, block the device or member at the server and suspend new protected sends until a verified Remove advances the epoch. Client checks remain authoritative for verifying the new roster.

## Sources

- [RFC 9420](https://www.rfc-editor.org/rfc/rfc9420.html), [RFC 9750](https://www.rfc-editor.org/rfc/rfc9750.html) and [RFC 9106](https://www.rfc-editor.org/rfc/rfc9106.html)
- [OpenMLS](https://github.com/openmls/openmls), [Wasm guidance](https://book.openmls.tech/user_manual/wasm.html), [SRLabs assessment](https://blog.openmls.tech/SRL-OpenMLS_security_assurance_assessment.pdf) and [security advisories](https://github.com/openmls/openmls/security/advisories)
- [ts-mls security policy](https://github.com/LukaJCB/ts-mls/security) and [Wire Core Crypto](https://github.com/wireapp/core-crypto)
- [Signal libsignal](https://github.com/signalapp/libsignal) and [Sesame](https://signal.org/docs/specifications/sesame/)
- [Matrix JavaScript SDK](https://github.com/matrix-org/matrix-js-sdk), [Matrix protocol](https://spec.matrix.org/latest/client-server-api/) and [Vodozemac assessment](https://matrix.org/media/Least%20Authority%20-%20Matrix%20vodozemac%20Final%20Audit%20Report.pdf)
- [Bouncy Castle Java](https://github.com/bcgit/bc-java) and [W3C Web Crypto](https://w3c.github.io/webcrypto/)
