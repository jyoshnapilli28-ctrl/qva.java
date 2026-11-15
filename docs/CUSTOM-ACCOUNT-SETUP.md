# QVA — Custom User Account Configuration & Operator Guide

**Document Version:** 1.0.0  
**Target Environment:** 4-User Private Windows Group Chat over Tailscale VPN  
**Status:** VALIDATED & TESTED (54/54 Automated Tests Passing)

---

## 1. 4-USER ACCOUNT TOPOLOGY

The QVA Private Chat system is designed for a fixed group of exactly four participants. Public self-registration and dynamic user creation are strictly disabled.

| Role | Username | Display Name | Device Type | Connection Method |
|---|---|---|---|---|
| **Host Laptop Operator** | `jyoshna` | Jyoshna | Central Server Host | Local (`http://localhost:8080/`) |
| **Client 1** | `meghana` | Meghana | Remote Laptop 1 | Tailscale VPN (`http://<tailscale-ip>:8080/`) |
| **Client 2** | `yash` | Yash | Remote Laptop 2 | Tailscale VPN (`http://<tailscale-ip>:8080/`) |
| **Client 3** | `yashuu` | Yashuu | Remote Laptop 3 | Tailscale VPN (`http://<tailscale-ip>:8080/`) |

---

## 2. DATABASE MIGRATION ARCHITECTURE (`V3__rename_accounts_to_custom_users.sql`)

### Migration Safety & Integrity
1. **Primary Key Preservation**: User IDs (`1`, `2`, `3`, `4`) are strictly preserved.
2. **Referential Integrity**: All message records (`messages.sender_id -> users.id`) maintain their exact foreign key associations.
3. **Case-Insensitive Resolution**: User lookups and authentication utilize `findByUsernameIgnoreCase`, so inputs like `Jyoshna`, `MEGHANA`, `Yash`, or `YaShUu` resolve cleanly to the canonical lowercase record.
4. **Idempotence**: `V3` utilizes conditional updates and `INSERT OR IGNORE` so both fresh installations and pre-existing databases converge to the exact four accounts without primary key collisions or orphaned records.

```sql
-- Migration V3 summary:
UPDATE users SET username = 'jyoshna', display_name = 'Jyoshna' WHERE LOWER(username) = 'alice';
UPDATE users SET username = 'meghana', display_name = 'Meghana' WHERE LOWER(username) = 'bob';
UPDATE users SET username = 'yash',    display_name = 'Yash'    WHERE LOWER(username) = 'carol';
UPDATE users SET username = 'yashuu',  display_name = 'Yashuu'  WHERE LOWER(username) = 'dave';

INSERT OR IGNORE INTO users (id, username, password_hash, display_name) VALUES
    (1, 'jyoshna', '$2a$12$SH9Nx9MTSmzHtFNjoPw6mOQvMAh9iVxuZw5x3v0kcaQFEzpFlyWDG', 'Jyoshna'),
    (2, 'meghana', '$2a$12$SH9Nx9MTSmzHtFNjoPw6mOQvMAh9iVxuZw5x3v0kcaQFEzpFlyWDG', 'Meghana'),
    (3, 'yash',    '$2a$12$SH9Nx9MTSmzHtFNjoPw6mOQvMAh9iVxuZw5x3v0kcaQFEzpFlyWDG', 'Yash'),
    (4, 'yashuu',  '$2a$12$SH9Nx9MTSmzHtFNjoPw6mOQvMAh9iVxuZw5x3v0kcaQFEzpFlyWDG', 'Yashuu');
```

---

## 3. ZERO-SHARED-CREDENTIAL SECURITY ENFORCEMENT

### Default Password `chat1234` Strictly Blocked
To guarantee that no deployment goes live with shared or default credentials:
1. `UserDetailsServiceImpl` actively intercepts the placeholder hash (`$2a$12$SH9Nx9MTSmzHtFNjoPw6mOQvMAh9iVxuZw5x3v0kcaQFEzpFlyWDG`).
2. Any login attempt with an uninitialized password is **immediately rejected with HTTP 401 Unauthorized**.
3. On application startup, `SqliteInitializer` audits the database and outputs a prominent security alert in the server terminal listing all uninitialized accounts.

---

## 4. OPERATOR INITIALIZATION WORKFLOW

Follow this exact sequence on the server host laptop (`jyoshna`) before sharing access with the other three laptops:

### Step 1: Start the Server to Apply Schema & Migrations
On the host laptop, open `release\Server\` and double-click **`Start-Server.bat`**.

During this initial startup:
- Flyway automatically applies `V1`, `V2`, and `V3`.
- The SQLite database is created at `release\Server\data\chat.db`.
- The console will display a warning:
  ```text
  [SECURITY ALERT] Uninitialized Account Passwords Detected!
  The following accounts still have placeholder/uninitialized passwords:
    * jyoshna (Logins are BLOCKED until initialized)
    * meghana (Logins are BLOCKED until initialized)
    * yash (Logins are BLOCKED until initialized)
    * yashuu (Logins are BLOCKED until initialized)
  ACTION REQUIRED: Run 'Set-Password.bat <username>' on the host server
  ```

### Step 2: Assign Individual Passwords for All 4 Users
Open a separate Command Prompt in `release\Server\` and run:

```cmd
Set-Password.bat jyoshna
Set-Password.bat meghana
Set-Password.bat yash
Set-Password.bat yashuu
```

For each user:
1. The tool verifies that the username is one of the four approved accounts.
2. Prompts interactively: `Enter new password for user '<username>':` (masked in console).
3. Prompts for confirmation: `Confirm new password for user '<username>':`.
4. Enforces the strong password policy:
   - **Minimum 12 characters**
   - **At least one uppercase letter (A-Z)**
   - **At least one lowercase letter (a-z)**
   - **At least one digit (0-9)**
   - **At least one special character (!@#$%^&*...)**
5. Generates an independent BCrypt hash (cost factor 12) with a unique cryptographic salt.
6. Updates `data\chat.db` and exits cleanly.

### Step 3: Verify Host Login
Open a browser on the host laptop to `http://localhost:8080/`.
- Log in with `username: jyoshna` and the newly assigned password.
- Verify entry into the chat room.

### Step 4: Distribute Client Packages
1. Ensure the host's Windows Firewall allows port 8080 over Tailscale:
   ```powershell
   New-NetFirewallRule -DisplayName "Private Chat Server" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8080 -RemoteAddress "100.64.0.0/10"
   ```
2. Note the host machine's Tailscale IPv4 address (`tailscale ip -4`).
3. Send `release\Private-Chat-Client.zip` to:
   - **Meghana** (along with her private password)
   - **Yash** (along with his private password)
   - **Yashuu** (along with her private password)
4. Each client extracts the ZIP, sets `SERVER_URL=http://<host-tailscale-ip>:8080` in `client-config.ini`, and launches `Start-Client.bat`.

