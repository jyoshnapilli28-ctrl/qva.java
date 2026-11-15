# Private VPN Chat — README

Private group chat for exactly four people, running over a Tailscale VPN network.

## Quick Start

### Server Laptop

1. Install [Java 17+](https://adoptium.net/) and [Tailscale](https://tailscale.com/download/windows).
2. Connect Tailscale: `tailscale up`
3. Open `deployment/Server/` and double-click **`Start-Server.bat`**.
4. Share the displayed Tailscale IP with the other members.

### Client Laptops (×3)

1. Install [Tailscale](https://tailscale.com/download/windows) and connect.
2. Unzip `Private-Chat-Client.zip`.
3. Edit `client-config.ini` and set `SERVER_URL=http://<server-tailscale-ip>:8080`.
4. Double-click **`Start-Client.bat`**.
5. Log in using your username and the shared password.

---

## Deployment Accounts

| Username | Role | Display Name | Credential Setup |
|----------|------|-------------|-----------------|
| `jyoshna` | Host Laptop Operator / User | Jyoshna | Run `Set-Password.bat jyoshna` |
| `meghana` | Client 1 User | Meghana | Run `Set-Password.bat meghana` |
| `yash`    | Client 2 User | Yash | Run `Set-Password.bat yash` |
| `yashuu`  | Client 3 User | Yashuu | Run `Set-Password.bat yashuu` |

> **Important Security Enforcement:** Logins for all accounts are strictly BLOCKED until the host operator initializes individual passwords using `Set-Password.bat <username>`. No shared or default password is accepted. See [docs/CUSTOM-ACCOUNT-SETUP.md](docs/CUSTOM-ACCOUNT-SETUP.md).

---

## Project Structure

```
private-vpn-chat/
├── pom.xml                          Maven build file
├── src/main/java/com/privatechat/
│   ├── PrivateChatApplication.java  Entry point
│   ├── config/                      Security, WebSocket, MVC config
│   ├── controller/                  REST API controllers
│   ├── service/                     Business logic
│   ├── repository/                  JPA repositories
│   ├── model/                       JPA entities
│   ├── websocket/                   STOMP handler + event listener
│   ├── dto/                         Transfer objects
│   └── exception/                   Error handling
├── src/main/resources/
│   ├── application.properties       Server config
│   ├── db/migration/V1__*.sql       Database schema + seed
│   └── static/                      Frontend (HTML/CSS/JS)
├── deployment/
│   ├── Server/                      Server package
│   └── Client/                      Client package
└── docs/                            Documentation
```

---

## Technology

| Layer | Technology |
|-------|-----------|
| VPN | Tailscale (WireGuard) |
| Backend | Java 17 + Spring Boot 3.2 |
| Real-time | Spring WebSocket + STOMP |
| Database | SQLite (WAL mode) |
| ORM | Spring Data JPA + Hibernate |
| Frontend | HTML + CSS + Vanilla JS |
| Launcher | Windows Batch (.bat) |

---

## Building from Source

```
mvn clean package -DskipTests
```

The JAR is output to `target/private-chat-server.jar`.

To run tests:
```
mvn test
```

---

## License

Private use only. Not for redistribution.

