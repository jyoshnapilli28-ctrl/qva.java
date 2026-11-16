# QVA 2.0 Final Test Results

Date: 2026-11-16. Commands below were run against this working tree. No production database was used.

## Environment

| Tool | Observed | Result |
|---|---|---|
| Node.js | v24.22.1 | Available |
| npm | 11.9.0 | Available (prints existing unknown `http-proxy` config warning) |
| Java | OpenJDK 21.0.13 runtime | Runtime only; does not match preferred Java 17 release setup |
| `javac` | command not found | Backend compilation blocked |
| Maven | command not found | Backend tests/package blocked |

## Executed checks

| Command | Result | Details |
|---|---|---|
| `npm run test:components` | PASS | 10 tests passed in 3 files, including 5 vault, 3 workspace, 2 messaging component tests. |
| `npm run build` | PASS | TypeScript check and Vite production build succeeded. |
| `VITE_QVA_LOCAL_VAULT=1 npm run build -- --outDir dist-vault` | PASS | Opt-in vault production build succeeded. Generated output was removed after recording. |
| `python3 tools/test-migrations-raw.py` | PASS | Executed V1–V8 SQL in numeric order, checked expected tables and four seed rows, closed/reopened DB and confirmed state persisted. This was Python sqlite3, not Flyway/Spring migration testing. |
| `npm test -- --reporter=line` | BLOCKED | 10 Playwright cases enumerated; failed at `QVA_TEST_PASSWORD` setup requirement; 6 skipped, 3 did not run. No packaged `QVA_TEST_BASE_URL` was available; packaged vault test did not execute. Exit code 1. |
| `java -version` | PASS | Runtime reports OpenJDK 21.0.13. |
| `javac -version` | BLOCKED | `/bin/bash: javac: command not found`. |
| `mvn -v` | BLOCKED | `/bin/bash: mvn: command not found`. |
| `git diff --check` | PASS | No whitespace errors in tracked diff. |

The first component-test attempt reported 9 passed and 1 failed because an existing Link-based login test lacked a router provider. The test was wrapped in `MemoryRouter`; the rerun passed all 10. The first TypeScript build also caught a missing parenthesis in the new invitation manager. It was fixed before the successful standard and vault-enabled builds. An initial raw-migration harness assertion used a mistaken table name; the corrected repeat is the passing script above. These local errors are retained here rather than hidden.

A prior attempt in this environment to launch the packaged Chromium vault run was blocked before browser interaction by `socket() failed: Operation not permitted (1)` and `ptrace: Operation not permitted (1)`. That was an environment failure, not a passing lifecycle test. No screenshot was captured.

## Not run / blocked

- Backend compilation, unit tests, integration tests, authorization regression, invitation Spring integration test, restart tests, packaged JAR build/startup: BLOCKED by missing Maven and Java compiler.
- Flyway migration test and V1–V8 application startup: NOT RUN. Only the raw SQL smoke test above ran.
- Packaged Playwright authentication, authorization, invite lifecycle, device, messaging, session and vault browser tests: BLOCKED; no packaged server and required test credentials/base URL were unavailable.
- Real Windows 11 host startup, Tailscale machine sharing, access-control policy, firewall, reboot, backup/restore, and external-member onboarding: NOT RUN.
- Real two-device messaging and MLS encryption/decryption: NOT RUN; MLS is not implemented.

## Change list

The full exact per-file status is included as `changed-files.txt` in the checkpoint ZIP. At this checkpoint, `git status --short` groups the current tree as follows:

```text
M pom.xml
M src/main/java/com/privatechat/config/SecurityConfig.java
M src/main/java/com/privatechat/config/WebMvcConfig.java
M src/main/java/com/privatechat/exception/GlobalExceptionHandler.java
M src/main/java/com/privatechat/repository/UserRepository.java
M src/main/java/com/privatechat/service/UserDetailsServiceImpl.java
M src/main/resources/application.properties
?? docs/architecture/
?? docs/qva2/
?? docs/security/
?? docs/testing/
?? frontend/
?? src/main/java/com/privatechat/config/QvaWebRoutes.java
?? src/main/java/com/privatechat/operations/
?? src/main/resources/application-prod.properties
?? src/main/resources/db/migration/V4__workspace_devices.sql
?? src/main/resources/db/migration/V5__direct_message_relay.sql
?? src/main/resources/db/migration/V6__conversation_lifecycle.sql
?? src/main/resources/db/migration/V7__device_crypto_identity.sql
?? src/main/resources/db/migration/V8__qva_member_invitations.sql
?? src/test/java/com/privatechat/operations/
?? tools/
```

The untracked entries include earlier QVA 2.0 work and are intentionally preserved; the exact manifest distinguishes the full working tree, while the implementation report identifies changes made during this freeze. No commit, push, deployment or production migration was performed.
