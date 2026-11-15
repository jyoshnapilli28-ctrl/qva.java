# Server Setup & Administration Guide

This guide describes how to configure, run, and administer the central Private Chat Server on Windows.

---

## 1. System Requirements

- **Operating System:** Windows 10 or Windows 11 (64-bit)
- **Java Runtime:** Java 17 LTS or higher (OpenJDK / Eclipse Adoptium Temurin recommended)
- **VPN:** Tailscale installed and joined to the group Tailnet
- **Network Port:** TCP 8080 (configurable via `server-config.properties`)
- **Disk Space:** ~100 MB for the application JAR + SQLite database storage

---

## 2. Step-by-Step Installation

### Step 1: Install Java 17+
1. Download OpenJDK 17 from [Eclipse Adoptium](https://adoptium.net/temurin/releases/?version=17).
2. Run the installer and ensure the option **"Set JAVA_HOME variable"** and **"Add to PATH"** are checked.
3. Open a command prompt and verify:
   ```cmd
   java -version
   ```
   Output must show version 17 or higher.

### Step 2: Install and Connect Tailscale
1. Download Tailscale from [tailscale.com/download/windows](https://tailscale.com/download/windows).
2. Install and launch the application.
3. Sign in to your private Tailscale network (all 4 machines must share the same network or be shared via Tailscale Node Sharing).
4. Run in command prompt:
   ```cmd
   tailscale ip -4
   ```
   Note down this IP address (e.g., `100.x.x.x`).

### Step 3: Configure Windows Firewall
To permit inbound chat traffic from client laptops over Tailscale, open port 8080 restricted to the Tailscale subnet (`100.64.0.0/10`):

Run PowerShell as **Administrator**:
```powershell
New-NetFirewallRule `
  -DisplayName "Private Chat Server" `
  -Direction Inbound `
  -Action Allow `
  -Protocol TCP `
  -LocalPort 8080 `
  -RemoteAddress "100.64.0.0/10"
```

---

## 3. Starting the Server

1. Navigate to the `Server` directory:
   ```cmd
   cd deployment\Server
   ```
2. Double-click or run from command line:
   ```cmd
   Start-Server.bat
   ```
3. The launcher terminal will display:
   - Java version verification
   - Tailscale connectivity and IP address detection
   - Database directory initialization
   - Real-time Spring Boot application logs
   - Target URL (e.g. `http://100.x.x.x:8080/`)

4. Keep this terminal window open. The server runs attached to the terminal.

---

## 4. Graceful Shutdown

To stop the server:
- Press `CTRL+C` in the terminal window running `Start-Server.bat`.
- The Spring Boot application context shuts down cleanly, closing active WebSocket sessions, disconnecting database pools, and checkpointing the SQLite Write-Ahead Log (WAL).
- The terminal will confirm:
  ```text
  Server stopped.
  Your chat database is preserved at: ...\data\chat.db
  ```

---

## 5. Database Persistence & Maintenance

- **Location:** `Server/data/chat.db`
- **Engine:** SQLite 3.45 with Write-Ahead Logging (`PRAGMA journal_mode=WAL`)
- **Foreign Keys:** Enforced (`PRAGMA foreign_keys=ON`)
- **Durability:** Survives machine reboots, server restarts, client disconnects, and JAR upgrades.

### Backing Up the Database
To back up the database while the server is running (or stopped):
```cmd
copy data\chat.db backups\chat-backup-%DATE:~10,4%%DATE:~4,2%%DATE:~7,2%.db
```

### Assigning Unique User Passwords Before Deployment
Deployment accounts configured in the database:
- `jyoshna` (Host laptop user)
- `meghana` (Client 1)
- `yash` (Client 2)
- `yashuu` (Client 3)

**CRITICAL SECURITY REQUIREMENT:**
Do not deploy to the group without setting individual passwords. Uninitialized logins are strictly blocked. Run the automated password utility on the server host:

```cmd
Set-Password.bat jyoshna
Set-Password.bat meghana
Set-Password.bat yash
Set-Password.bat yashuu
```
This utility:
1. Interactively prompts for the new password (with input masked in console).
2. Computes a secure BCrypt hash with cost factor 12.
3. Updates `data/chat.db` directly and exits cleanly.
4. Never writes or logs the plaintext password.

---

## 6. Configuration Reference (`server-config.properties`)

| Key | Default | Description |
|---|---|---|
| `SERVER_PORT` | `8080` | TCP port Spring Boot binds to |

Environment variables supported by the application JAR:
- `DB_PATH`: Absolute or relative path to the SQLite file (defaults to `./data/chat.db`).
- `server.port`: Overrides HTTP port.

