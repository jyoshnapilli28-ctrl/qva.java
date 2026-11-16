# QVA 2.0 Release Verification Checklist

Completed checks apply to the published implementation `a5a79658296ea73f7828aa593d45a52de8ef8395` and are supported by [the verification transcript](QVA_2.0_TEST_EVIDENCE.txt). Windows and production deployment checks remain separate.

## Source and build

- [ ] Review complete diff and preserve all pre-existing work.
- [ ] On Windows 11 with Java 17 Temurin, Maven 3.9.x and supported Node/npm, run `mvn -Pqva-web clean verify` (and the project's documented test commands).
- [x] Compile on Linux and verify invitations, Flyway V1–V8, the V7-to-V8 upgrade, backend regressions, and restart behavior with disposable databases.
- [x] Run frontend component tests and the standard and `VITE_QVA_LOCAL_VAULT=1` production builds.
- [x] Start the production-profile JAR on loopback and run the packaged Playwright HTTP check.
- [ ] Run Playwright session, invitation, device, conversation and vault lifecycle suites in a supported browser. Chromium startup was blocked during Linux verification.
- [ ] Scan built assets and source logs for secrets; verify vault password and private keys are absent from requests, server state and logs.
- [x] Verify production static assets include the built React workspace under `/app/` and preserve the classic root UI.

## Database and identity

- [ ] Back up the existing HOST data directory and verify a restore to a separate disposable path.
- [ ] Review Flyway plan for additive V8 before a deliberate production upgrade; preserve accounts, credentials, messages and host grants.
- [ ] Confirm exactly the existing authorized HOST grant remains; no new HOST is created.
- [x] Confirm on disposable fixtures that a registrant is MEMBER, a new device is PENDING, HOST approval is separate, and device revocation denies later protected REST access.

## Windows and Tailscale

- [ ] Install/start Tailscale on the HOST and verify private Serve for loopback port 8080; do not enable Funnel or router forwarding.
- [ ] Review existing Tailscale sharing, ACL/grant and Windows Defender Firewall rules before changes; restrict shared access to the intended app port.
- [ ] Test from one external Tailscale account before inviting the group; verify the app is reachable and unrelated ports are not.
- [ ] Restart and reboot the HOST; verify QVA, database persistence, session behavior and private Serve status.
- [ ] Verify member onboarding with separate Tailscale and QVA credentials, invitation expiry/revocation, device approval and account/device revocation.

## Cryptographic release gate

- [ ] Keep production messaging disabled until a vetted MLS integration and acceptance suite prove identity binding, encryption/decryption, membership changes and rekey.
- [ ] Keep synthetic fixtures labeled synthetic; do not claim E2EE.
- [ ] Run packaged vault lifecycle tests and explicitly accept PBKDF2's provisional status and the unrecoverable-passphrase limitation.

## Release action

- [ ] Review release report and known limitations.
- [ ] Only then perform the separately authorized commit, push and Windows deployment operation.
