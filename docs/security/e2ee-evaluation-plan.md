# E2EE evaluation plan

Status: research candidates only. No protocol selected or installed.

- MLS, RFC 9420 and RFC 9750. Evaluate a maintained browser/WASM binding and how QVA would supply credential authentication, key packages, commit ordering, group epochs and delivery without altering the protocol.
- Signal-style protocols. Verify the official library's supported client platforms and licensing. Evaluate direct asynchronous delivery, device verification and prekeys. Reject unsupported browser bindings and account for group membership separately.
- Matrix Rust crypto through the Matrix JavaScript SDK. Evaluate the browser/WASM integration and its room, identity and sync requirements. Measure the architectural cost before considering a separate service or adopting any dependency.
- Web Crypto is a platform primitive API, not a messaging protocol. Evaluate it only where the chosen implementation or reviewed vault design requires it.

Primary references: https://www.rfc-editor.org/rfc/rfc9420.html , https://www.rfc-editor.org/info/rfc9750/ , https://github.com/signalapp/libsignal , https://matrix-org.github.io/matrix-js-sdk/ .

For each candidate record release and maintenance evidence, audited versions, license, browser and Windows build, Java relay fit, interoperability, groups, device add/remove, forward secrecy, post-compromise behavior, offline recovery and supply-chain controls. Demonstrate two independent browser profiles and group membership changes with upstream vectors before recommending a candidate.

Document the threat model for a malicious network, compromised relay/database, stolen account or browser state, replay, tampering, password guessing and endpoint compromise. HTTPS and an independently trustworthy client delivery strategy are distinct controls. A relay that can replace client code can subvert browser encryption. Investigate password-separated vault wrapping with a reviewed KDF and honest recovery limits. Obtain approval of the decision and threat model before implementing the protocol.
