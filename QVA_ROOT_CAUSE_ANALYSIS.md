# QVA — SYSTEM ROOT CAUSE ANALYSIS (RCA)

**Project:** QVA — Private Java-Based 4-Device Chat Application  
**Investigation Scope:** Local Source Code, Spring Boot Configuration, SQLite Engine, Batch Scripts, and College Wi-Fi Network  
**Investigation Date:** November 15, 2026  
**Status:** COMPLETED — ALL ROOT CAUSES IDENTIFIED, RESOLVED, OR ISOLATED  

---

## 1. INCIDENT & GAP ROOT CAUSE INVESTIGATION

During the verification and deployment preparation of QVA, eight distinct technical anomalies were encountered across application code, runtime environments, and network infrastructure. This document provides the engineering root cause, reproduction mechanism, and implemented fix for each.

---

### RCA-01: Windows JVM `DB_PATH` Argument Quote-Wrapping
- **Symptom:** When running `Start-Server.bat` or `Set-Password.bat`, SQLite failed to resolve the database path or attempted to create files with literal quotation marks in the filename.
- **Root Cause:** In Windows batch scripts, parameters passed as `"-DDB_PATH=%DATA_DIR:\=/%/chat.db"` retain the enclosing double quotes when parsed by Java's `System.getProperty("DB_PATH")`. The resulting JDBC URL became `jdbc:sqlite:"C:/Users/JOSHU/qva.deploy/release/Server/data/chat.db"`. SQLite's native driver treats the literal quotation marks as part of the filesystem path, causing filesystem failures or nested directories.
- **Remediation:** Added `sanitizeDbPath()` in `PrivateChatApplication.java`. The sanitizer checks for leading/trailing single or double quotes, strips them, trims whitespace, updates the system property, and automatically creates parent directories (`parentDir.mkdirs()`) before SQLite and Flyway initialize.

---

### RCA-02: Abrupt JVM Halt in `PasswordManagementRunner`
- **Symptom:** In earlier iterations of the password assignment utility, passwords appeared to be assigned successfully in console output, but on subsequent server startup, the database still contained the uninitialized hash.
- **Root Cause:** `PasswordManagementRunner.run()` was annotated with `@Transactional`. When `System.exit(0)` or `SpringApplication.exit()` was called at the end of the method, the JVM process was terminated before the Spring transaction interceptor executed its post-invocation commit hook. As a result, the SQLite uncommitted transaction was rolled back on process exit.
- **Remediation:** Removed `@Transactional` from `run()`. Injected `PlatformTransactionManager` and wrapped the database write in an explicit `TransactionTemplate.execute(...)` block. This forces an immediate, synchronous transaction commit and flushes SQLite to disk before `SpringApplication.exit()` is called.

---

### RCA-03: SQLite Lock Contention on Application Startup
- **Symptom:** Intermittent `SQLITE_BUSY: database is locked` errors during rapid server restarts or integration testing.
- **Root Cause:** `SqliteInitializer.applyPragmas()` was executing `PRAGMA journal_mode=WAL` unconditionally on every startup event. If a background Hikari connection pool thread or Flyway verification had already opened a read transaction on `chat.db`, executing `PRAGMA journal_mode=WAL` requires an exclusive lock that times out.
- **Remediation:** Updated `SqliteInitializer` to first execute `PRAGMA journal_mode` (a shared read operation). If the return value is already `"wal"`, the change command is skipped entirely. Foreign keys, synchronous normal, and busy timeout (5000ms) are configured safely.

---

### RCA-04: Static Asset HEAD Request 401 Unauthorized
- **Symptom:** Automated uptime probes, reverse proxies, and curl health checks using `HEAD /` received HTTP 401 Unauthorized instead of 200 OK.
- **Root Cause:** In `SecurityConfig.java`, static asset authorizations were declared as:
  ```java
  .requestMatchers(HttpMethod.GET, "/", "/index.html", "/css/**", "/js/**", "/favicon.ico").permitAll()
  ```
  Because the matcher was explicitly restricted to `HttpMethod.GET`, any `HEAD` request fell through to the default rule: `.anyRequest().authenticated()`, resulting in a 401 response.
- **Remediation:** Removed the HTTP method filter:
  ```java
  .requestMatchers("/", "/index.html", "/css/**", "/js/**", "/favicon.ico").permitAll()
  ```
  This permits `GET`, `HEAD`, and `OPTIONS` for all static frontend assets. Validated via `StaticAssetTest`.

---

### RCA-05: Reverse Proxy Session Dropping Behind Cloudflare
- **Symptom:** Users connecting over HTTPS tunnels were repeatedly logged out or experienced 403 Forbidden errors during WebSocket handshakes.
- **Root Cause:** When traffic traverses Cloudflare Tunnel, the outer connection is HTTPS, but the internal connection from `cloudflared` to Tomcat is HTTP (`http://127.0.0.1:8080`). Tomcat's session cookie was generated without recognizing the upstream `X-Forwarded-Proto: https` header, causing browsers to treat cookies as insecure or drop cross-site session cookies.
- **Remediation:** Added the following properties to `application.properties`:
  ```properties
  server.forward-headers-strategy=framework
  server.servlet.session.timeout=24h
  server.servlet.session.cookie.http-only=true
  server.servlet.session.cookie.same-site=lax
  ```
  Tomcat now inspects standard reverse-proxy headers and generates proper session cookies.

---

### RCA-06: WebSocket and REST CORS Handshake Rejection
- **Symptom:** WebSocket connections from `https://*.trycloudflare.com` failed with HTTP 403 Forbidden during initial STOMP handshake.
- **Root Cause:** Spring WebSocket (`WebSocketConfig`) and Spring MVC (`WebMvcConfig`) had strict origin restrictions that did not allow dynamically generated Cloudflare quick tunnel subdomains.
- **Remediation:** Configured dynamic origin patterns on both controllers:
  ```java
  registry.addEndpoint("/ws/chat")
          .setAllowedOriginPatterns("https://*.trycloudflare.com", "https://*.cloudflareaccess.com", "http://100.*", "http://localhost:*", "http://127.0.0.1:*")
          .withSockJS();
  ```
  This permits legitimate Cloudflare and Tailscale traffic while rejecting arbitrary untrusted web origins.

---

### RCA-07: Port 8080 Conflict on Host Laptop
- **Symptom:** Spring Boot startup failed with `Web server failed to start. Port 8080 was already in use.`
- **Root Cause:** A previous background JVM process (`PID 27028`) started during earlier verification remained running and held socket `127.0.0.1:8080`.
- **Remediation:** Identified the owning process via `Get-NetTCPConnection` and `Win32_Process`, terminated the stale instance (`Stop-Process -Id 27028 -Force`), and verified socket availability via Python socket binding.

---

### RCA-08: Cloudflare Tunnel Edge Disconnection on College Wi-Fi
- **Symptom:** `cloudflared tunnel` failed to connect to Cloudflare edge nodes, logging:
  `TLS handshake with edge error: read tcp 10.4.112.19:62338->198.41.192.167:7844: wsarecv: An existing connection was forcibly closed by the remote host.`
- **Root Cause:**
  1. The host laptop is connected to an institutional College Wi-Fi network (`10.4.112.19`).
  2. The `cloudflared` client requires outbound connections to Cloudflare's edge on **TCP/UDP port 7844** (control plane protocol).
  3. Official Cloudflare architecture hardcodes port 7844 into the daemon; it cannot be remapped to port 443.
  4. While raw TCP SYN packets to 198.41.192.167:7844 succeed (`TcpTestSucceeded: True`), the institutional firewall performs Deep Packet Inspection (DPI) and actively issues TCP RST resets (`WSAECONNRESET` / `wsarecv: An existing connection was forcibly closed`) as soon as the TLS Client Hello is transmitted.
- **Remediation & Workaround Strategy:**
  - Local repairs, packaging, and automated tests are 100% complete and independent of external networks.
  - For remote multi-device chat on college Wi-Fi: Deploy via **Tailscale VPN** (which utilizes NAT-traversal and DERP relays over standard HTTPS 443) OR switch the host laptop to a personal mobile hotspot where outbound port 7844 is unrestricted.

---

## 2. SUMMARY OF DEFENSIVE MEASURES IMPLEMENTED

1. Robust sanitization of all command-line and environment parameters.
2. Synchronous, deterministic database transaction commits.
3. Lock-free SQLite startup checks.
4. Comprehensive HTTP method support for static assets.
5. Production-grade reverse proxy and CORS headers.
6. Empirical network diagnostic procedures documented for host operators.

