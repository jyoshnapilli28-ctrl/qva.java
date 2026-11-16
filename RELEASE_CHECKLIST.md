# QVA 2.0 Release Verification Checklist

This list is for the separate release operation. Nothing below was deployed or committed during the final implementation freeze.

## Source and build

- [ ] Review complete diff and preserve all pre-existing work.
- [ ] On Windows 11 with Java 17 Temurin, Maven 3.9.x and supported Node/npm, run `mvn -Pqva-web clean verify` (and the project's documented test commands).
- [ ] Compile and run the invitation lifecycle, V1–V8 Flyway migration, existing backend regression and restart tests against disposable databases.
- [ ] Run frontend components, standard production build and `VITE_QVA_LOCAL_VAULT=1` production build.
- [ ] Run packaged JAR startup and Playwright session, invitation, device, conversation and vault lifecycle suites in a supported browser.
- [ ] Scan built assets and source logs for secrets; verify vault password and private keys are absent from requests, server state and logs.
- [ ] Verify production static assets include the built React workspace under `/app/` and preserve the classic root UI.

## Database and identity

- [ ] Back up the existing HOST data directory and verify a restore to a separate disposable path.
- [ ] Review Flyway plan for additive V8 before a deliberate production upgrade; preserve accounts, credentials, messages and host grants.
- [ ] Confirm exactly the existing authorized HOST grant remains; no new HOST is created.
- [ ] Confirm a registered member is MEMBER, a new device is PENDING, host approval is separate, and revocation blocks future app access.

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
