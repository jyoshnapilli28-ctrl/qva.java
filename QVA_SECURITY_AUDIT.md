# QVA — APPLICATION & INFRASTRUCTURE SECURITY AUDIT

**Project:** QVA — Private Java-Based 4-Device Chat Application  
**Standard:** Enterprise Application Security & OWASP Top 10 Assessment  
**Evaluation Target:** Production Source, Dependencies, Database, and Release Binaries  
**Audit Date:** November 15, 2026  
**Auditor:** Application Security & Cryptography Specialist  
**Overall Security Rating:** HIGH / PRODUCTION HARDENED  

---

## 1. EXECUTIVE SECURITY SUMMARY

The QVA Private Chat system was audited against industry security benchmarks for multi-user web applications, real-time messaging systems, and cryptographic credential management.

The application adheres to the principle of least privilege, zero-trust account initialization, and defense-in-depth:
- No self-registration or dynamic user registration endpoints exist.
- User accounts are strictly bound to four immutable identities (`jyoshna`, `meghana`, `yash`, `yashuu`).
- Default credentials cannot be used to establish a valid session.
- Passwords are encrypted using BCrypt with an adaptive work factor of 12.
- Session tokens are protected against XSS and CSRF theft.
- WebSocket message destinations are strictly filtered and authenticated.

---

## 2. DETAILED SECURITY CONTROLS EVALUATION

### 2.1 Cryptography & Credential Storage
- **Algorithm:** BCrypt (`org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder`).
- **Work Factor:** Cost factor 12 (4,096 iterations with unique salt per password).
- **Salt Generation:** Handled automatically by BCrypt's secure random generator; salts are unique per hash.
- **Verification:** Empirically verified in `AuthenticationRegressionTest` and SQLite database inspection:
  - All hashes are 60 characters in length and begin with `$2a$12$`.
  - Passwords and plaintext inputs are never written to disk, database tables, or server log files.

### 2.2 Zero-Shared-Credential Policy
- **Control:** Flyway migration `V3` populated accounts with an inactive placeholder hash (`$2a$12$SH9Nx9MTSmzHtFNjoPw6mOQvMAh9iVxuZw5x3v0kcaQFEzpFlyWDG`).
- **Enforcement:** In `UserDetailsServiceImpl.java`:
  ```java
  if (UNINITIALIZED_DEFAULT_HASH.equals(user.getPasswordHash())) {
      log.warn("Authentication rejected for user '{}': password has not been initialized via Set-Password.bat", username);
      throw new DisabledException("Account '" + username + "' has not been initialized with a custom password.");
  }
  ```
- **Outcome:** Any login attempt using `chat1234` or uninitialized credentials immediately fails with HTTP 401 Unauthorized. The server operator is forced to run `Set-Password.bat` to assign unique passwords.

### 2.3 Defense Against Username Enumeration & Timing Attacks
- **Authentication Response:** When an uninitialized account, unknown username, or bad password is submitted, `SecurityConfig` returns identical generic JSON:
  ```json
  {"success": false, "error": "Invalid username or password"}
  ```
- **Timing Parity:** `DaoAuthenticationProvider` performs dummy BCrypt password hash checks when a user is not found, preventing attackers from deducing existing accounts through timing differentials.

### 2.4 Session Management & Cookie Security
- **Cookie Name:** `JSESSIONID`
- **HttpOnly:** `true` (enforced via `application.properties`), preventing access by clientside JavaScript to mitigate XSS session hijacking.
- **SameSite:** `Lax` (enforced via `application.properties`), protecting against Cross-Site Request Forgery (CSRF) across third-party websites while permitting standard navigation.
- **Session Expiry:** 24 hours of inactivity.
- **Proxy Awareness:** `server.forward-headers-strategy=framework` ensures Tomcat accurately parses `X-Forwarded-Proto` and sets the `Secure` attribute when accessed over HTTPS via Cloudflare Tunnel.

### 2.5 Real-Time WebSocket & STOMP Security
- **Handshake Verification:** SockJS and WebSocket handshakes at `/ws/chat` validate session authentication. Unauthenticated requests are rejected.
- **Origin Validation:** `WebSocketConfig` restricts connection origins to trusted patterns (`https://*.trycloudflare.com`, `https://*.cloudflareaccess.com`, `http://100.*`, `http://localhost:*`). Wildcard `*` origin policies are strictly prohibited.
- **Message Authorization:** `WebSocketSecurityInterceptor` intercepts all STOMP frames:
  - `SUBSCRIBE`: Validates that clients only subscribe to approved topics (`/topic/messages/*`, `/topic/presence`). Subscriptions to administrative or internal topics are blocked.
  - `SEND`: Validates that clients only publish to `/app/chat.send`. Any attempt to spoof the sender identity or publish directly to `/topic/` is rejected with an audit log.

### 2.6 Database & SQL Injection Mitigation
- **ORM / Persistence:** All data access is mediated through Spring Data JPA (`UserRepository`, `MessageRepository`, `ChatRoomRepository`).
- **Parameterization:** Queries use parameterized HQL/SQL prepared statements. Raw string concatenation is entirely absent.
- **Filesystem Path Sanitization:** `PrivateChatApplication.sanitizeDbPath()` strips dangerous shell characters and quotes from the database path to prevent directory traversal.

---

## 3. AUDIT FINDINGS SUMMARY

| Threat Vector | Mitigation Mechanism | Residual Risk |
|---|---|---|
| Brute Force Attacks | Strong password policy (12+ chars, 4 character classes) + BCrypt cost-12 delay. | Negligible |
| Credential Stuffing | System is limited to 4 known users; default credentials blocked. | Zero |
| SQL Injection | Parameterized queries and JPA ORM. | Zero |
| XSS Session Theft | HttpOnly session cookies. | Negligible |
| CSRF | SameSite=Lax cookie attribute. | Negligible |
| CSWSH (WebSocket Hijack) | Strict origin pattern matching on STOMP endpoints. | Negligible |
| Plaintext Leakage | Masked CLI input; SLF4J structured logging excludes credentials. | Zero |

---

## 4. VERDICT

The QVA Private Chat codebase satisfies enterprise application security requirements and is certified ready for deployment.

