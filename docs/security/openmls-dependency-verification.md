# M6.2 OpenMLS dependency verification

**Reviewed 15 November 2026. Candidate is blocked from integration.**

## Verified release and advisories

The crates.io package documentation index lists OpenMLS 0.9.2, published on 22 October 2026. A docs.rs build is evidence of publication, not a QVA Wasm worker test. The upstream OpenMLS project uses MIT licensing. The RustCrypto provider also declares MIT. The prior SRLabs assessment's scope does not approve the provider, QVA worker or distribution.

| Upstream advisory | Patched release relevant to the review |
| --- | --- |
| GHSA-94v4-rg25-x625 | 0.9.2 for the 0.9 series |
| GHSA-p276-g3f3-wwgh | 0.9.2 for the 0.9 series |
| GHSA-gc79-23g3-8g52 | 0.9.1 for the 0.9 series |
| GHSA-w62v-gv48-63rh | 0.9.0 |
| GHSA-rrmv-c79f-cf5r | 0.9.0 |
| GHSA-8x3w-qj7j-gqhf | 0.7.2, superseded by the 0.9 candidate |

The Oct 22 fixes set 0.9.2 as the minimum release evaluated on the 0.9 line. Recheck all upstream and transitive advisories on the exact resolved versions before integration.

## Published dependency metadata

| Component | Published requirements and decision |
| --- | --- |
| OpenMLS 0.9.2 | Published normal requirements include openmls_traits ^0.6.0, openmls_serialization_helpers ^0.1.0, tls_codec ^0.5, log ^0.4, rayon ^1.12.0, serde ^1.0, serde_bytes ^0.11.19, thiserror ^2.0, and zeroize ^1.9.0. |
| Candidate browser provider | Optional publishing metadata lists openmls_rust_crypto ^0.6.0, openmls_basic_credential ^0.6.0, openmls_memory_storage ^0.6.0, and browser-related getrandom ^0.4.3 and ^0.2.17 and web-time ^1.1. Feature selection and exact platform graph must be resolved together. |
| Published openmls_rust_crypto 0.6.0 | Declares hpke-rs, hpke-rs-crypto and hpke-rs-rust-crypto ^0.7, and ed25519-dalek ^2.0. The main-branch Cargo manifest advertises different versions despite keeping the same provider package version. The published crate's metadata is the reference for a pinned build. |

These are version requirements, not exact transitive resolutions. The package-specific Cargo manifest/source metadata was inaccessible during review. The retrieved latest upstream lockfile page reported 0.9.1 despite the published crate index showing 0.9.2. No cryptographic build graph or checksum was inferred from that snapshot. The available local runtime was Linux and did not expose Cargo or Rustup on PATH.

## Gates before integration

1. Retrieve the published OpenMLS 0.9.2 archive and match the registry checksum, source commit and vendor metadata. Pin the toolchain and the exact browser-provider crate/features in an isolated build. Avoid optional draft/PQ features in this phase.
2. Resolve the exact Wasm browser target lockfile and SBOM, including storage, RNG, signing, AEAD and HPKE dependencies. Audit the exact version/license graph and source provenance.
3. Verify the production Wasm worker, entropy, storage, interoperability and packaging on the intended Windows runtime. Record native Windows testing separately from browser or WebView2 execution.
4. Keep the Spring Boot foundation free of MLS libraries while these gates remain unresolved. The shell/installer signing and independent update trust required for malicious-HOST resistance have design approval and remain future work.

## Sources

- [OpenMLS 0.9.2 release and dependency index](https://docs.rs/crate/openmls/latest) and [build information](https://docs.rs/crate/openmls/latest/builds)
- [OpenMLS advisory index](https://github.com/openmls/openmls/security/advisories) and [upstream repository](https://github.com/openmls/openmls)
- [Published RustCrypto provider](https://docs.rs/crate/openmls_rust_crypto/0.6.0), [main-branch provider manifest](https://github.com/openmls/openmls/blob/main/openmls_rust_crypto/Cargo.toml), and [Wasm guidance](https://book.openmls.tech/user_manual/wasm.html)
