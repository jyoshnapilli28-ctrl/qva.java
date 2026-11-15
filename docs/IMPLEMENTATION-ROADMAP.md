# Private VPN + Chat Room — Implementation Roadmap

**Project type:** Small, private, four-person Windows network with a persistent chat room  
**Primary backend language:** Java  
**Prototype UI:** Deliberately low-fidelity HTML/CSS/JavaScript  
**Target cost:** ₹0 / $0 recurring software and hosting cost, subject to the free-plan conditions below

---

## 1. Goal and scope

Build a private network for four people. One Windows laptop hosts the application and database; the other three laptops connect to it and use a shared chat room. The host laptop can also open the chat UI locally.

The prototype must:

- Work across different networks, not only when all four laptops are on the same Wi-Fi.
- Keep the backend primarily in Java.
- Provide a basic, intentionally low-fidelity browser UI while the backend is being built.
- Allow all four members to send and receive messages in one shared room.
- Store each message in a database with a server-generated timestamp.
- Preserve message history when a browser closes, a user logs out, or the server application restarts.
- Allow a member to reconnect later and retrieve the previous chat history.
- Run without paid hosting, a paid domain, a paid database, or a paid UI service.

### Important interpretation

This is a **centralised chat application over a private VPN**, not a new VPN protocol implementation. The VPN provides private connectivity between the laptops. The Java application provides the chat room, authentication, message delivery, and message history.

For the first prototype, all chat messages pass through the host laptop. The three client laptops do not communicate directly with each other at the application layer.

---

## 2. Recommended architecture

### Network layer: Tailscale

Use **Tailscale** as the private VPN/overlay network. It uses WireGuard for encrypted device connectivity and avoids the need to implement cryptography, VPN handshakes, key exchange, or NAT traversal yourself.

The current Tailscale Personal plan is listed as free for up to six users, which is sufficient for a four-person group. It is intended for personal, non-commercial use. Each person should use their own Tailscale identity; do not share one account. Check the plan and terms again when setting it up because provider limits can change.

Tailscale can establish direct device connections or fall back to encrypted relay connections when direct connectivity is blocked by network/NAT conditions. A relay can add latency, but it means the group does not have to rely on router port forwarding in the normal setup.

Official references:

- [Tailscale pricing and Personal plan](https://tailscale.com/pricing)
- [Tailscale free-plan details](https://tailscale.com/docs/account/manage-plans/free-plans-discounts)
- [Tailscale connection types](https://tailscale.com/docs/reference/connection-types)
- [Tailscale encryption](https://tailscale.com/docs/concepts/tailscale-encryption)
- [Tailscale installation on Windows](https://tailscale.com/docs/install/windows)

### Application layer: Java + Spring Boot

Run a Spring Boot application on the host laptop. It serves the UI, exposes the history/login APIs, accepts live messages, and writes messages to SQLite.

Recommended stack:

| Layer | Technology | Reason |
|---|---|---|
| VPN | Tailscale for Windows | Free Personal plan for this group size; private connectivity without self-hosting a VPN gateway |
| Runtime | Java 17+ LTS | Long-term-support Java release |
| Backend | Spring Boot 3.2+ | Java HTTP server, dependency management, security and WebSocket support |
| Build | Maven | Reproducible build across developer environments |
| API | Spring MVC REST | Login, identity and paginated message history |
| Live messaging | Spring WebSocket with STOMP/SockJS | Real-time message delivery to connected browsers |
| Database | SQLite | A single local database file is sufficient for four users and low message volume |
| Database access | Spring Data JPA / Hibernate Community Dialect | Robust object-relational mapping and schema management |
| Password storage | Spring Security password encoder (BCrypt) | Stores password hashes rather than plaintext passwords |
| Frontend | HTML, CSS, vanilla JavaScript | Fast to build, easy to inspect, no frontend framework required |
| Tests | JUnit 5, Spring Boot Test | Backend, security and persistence tests |

---

## 3. Cost and operational constraints

### Expected cost

| Component | Cost target |
|---|---:|
| Tailscale Personal plan | $0 for up to six users, subject to plan terms |
| Java / Spring Boot | $0 |
| Maven | $0 |
| SQLite | $0 |
| HTML/CSS/JavaScript | $0 |
| Hosting | $0 — the existing host laptop is the server |
| Domain name | Not required |
| Cloud database | Not required |
| TLS certificate | Not required for the initial private-VPN-only prototype |

No cloud VM or paid hosting is part of this plan. Internet access and electricity are existing user costs; they are not included as new project subscriptions.

### Availability limitation

The host laptop is the server. **If it is shut down, disconnected from the internet, or asleep, the other three laptops cannot use the chat room.** A local SQLite database preserves messages, but it cannot make an offline host reachable.

For the prototype:

- Keep the host laptop powered and connected to the internet during expected chat hours.
- Configure Windows power settings so it does not sleep while plugged in.
- Start the Java application after Windows starts, preferably through Task Scheduler after the prototype is stable.
- Keep the database on the host laptop's local disk, not in a synchronised cloud folder.

---

## 4. MVP feature definition

### Included in the first prototype

1. One persistent room named `General`.
2. Four named member accounts: one host/admin and three members (`alice`, `bob`, `carol`, `dave`).
3. Login and logout.
4. Live text messages visible to all connected room members.
5. Message history loaded from SQLite when a member joins or reconnects.
6. Server-generated UTC timestamps, displayed in each viewer's local time zone.
7. A basic online/offline connection indicator.
8. Basic validation and error handling.
9. A database file that remains after the application is stopped or restarted.
10. A simple backup/export procedure for the database.

### Explicitly out of scope for the first prototype

- Building a VPN protocol or custom encryption implementation.
- Direct peer-to-peer chat between client laptops.
- Private messages, multiple rooms, channels or group management UI.
- File transfers, voice/video calls, screen sharing or presence history.
- End-to-end encrypted chat where the host server cannot read message contents.
- Mobile applications.
- Public internet access to the chat server outside the private VPN.
- Cloud hosting or a paid domain.

---

## 5. Low-fidelity UI specification

The UI is a functional, restrained interface:

```text
+--------------------------------------------------------------+
| PRIVATE CHAT / GENERAL                 VPN: CONNECTED         |
+----------------------+---------------------------------------+
| MEMBERS              | GENERAL                               |
|                      |                                       |
| Host                 | [16:03] Alex: Message text...          |
| Member 2             | [16:04] Sam: Message text...           |
| Member 3             | [16:05] Host: Message text...          |
| Member 4             |                                       |
|                      |                                       |
+----------------------+---------------------------------------+
| Message...                                  [ Send ]          |
+--------------------------------------------------------------+
```

### Visual rules

- Light background (#f0f2f5).
- Thin borders (#dde1e7) and simple rectangular sections.
- System font stack; no custom font download.
- No gradients, glassmorphism, animated backgrounds, glow effects, or decorative illustrations.
- Clear timestamps and sender names.
- One message composer fixed at the bottom of the chat panel.
- Semantic HTML and accessible inputs.

---

## 6. Backend design

### Main responsibilities

| Component | Responsibility |
|---|---|
| `AuthController` | Login, logout, current-user endpoint (`/api/auth/me`), and status (`/api/auth/status`) |
| `MessageController` | Paginated message history (`/api/rooms/{room}/messages`) |
| `UserController` | List users and presence status (`/api/users`) |
| `ChatWebSocketController` | Inbound STOMP message handler (`/app/chat.send`) |
| `WebSocketSecurityInterceptor` | Channel authorization for subscriptions and sends |
| `MessageService` | Validate message payload, save to SQLite, broadcast to topic |
| `OnlinePresenceService` | Track connected members and broadcast presence events |
| `UserDetailsServiceImpl` | Load users and BCrypt hashes for Spring Security |
| `SecurityConfig` | Configure form login, session cookies, and public vs protected endpoints |

---

## 7. Database design

SQLite database file stored at:
```text
Server/data/chat.db
```

### Tables
- `users`: `id`, `username`, `password_hash`, `display_name`, `created_at`, `last_login`, `is_active`
- `chat_rooms`: `id`, `name`, `description`, `created_at`
- `messages`: `id`, `room_id`, `user_id`, `content`, `sent_at`, `is_deleted`

### Key database settings
- `PRAGMA journal_mode=WAL` (Write-Ahead Logging)
- `PRAGMA foreign_keys=ON`
- `PRAGMA synchronous=NORMAL`
- UTC timestamps stored in ISO-8601 format

---

## 8. Security and network configuration

- **Tailscale VPN:** Mesh network encrypts all communication end-to-end via WireGuard.
- **Firewall:** Windows Defender Firewall restricted to `100.64.0.0/10` for TCP port `8080`.
- **BCrypt:** Passwords hashed using cost 12 BCrypt algorithm.
- **WebSocket Channel Security:** Unauthenticated users cannot subscribe to topics or send messages.
- **Session:** HTTP-only, SameSite=Strict cookies with 24-hour expiration.

---

## 9. Launchers & Packaging

1. **Server (`Start-Server.bat`):**
   - Detects Java 17+
   - Checks Tailscale and discovers IPv4 address
   - Prepares `data/` directory
   - Launches `private-chat-server.jar` attached to terminal
   - Supports graceful shutdown via `CTRL+C`

2. **Client (`Start-Client.bat`):**
   - Requires zero Java, Maven, or development tools
   - Validates Tailscale installation
   - Reads `client-config.ini`
   - Verifies server connectivity via `/api/auth/status`
   - Automatically opens default browser to the chat room

