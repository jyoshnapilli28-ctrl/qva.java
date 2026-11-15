================================================================================
                    PRIVATE CHAT SERVER — OPERATOR GUIDE
================================================================================

This package contains the central server for the 4-person private chat network.

--------------------------------------------------------------------------------
1. PREREQUISITES
--------------------------------------------------------------------------------
- Windows 10 or 11 (64-bit)
- Java 17 or later (Eclipse Adoptium Temurin recommended: https://adoptium.net)
- Tailscale installed and connected (https://tailscale.com/download/windows)

--------------------------------------------------------------------------------
2. QUICK START
--------------------------------------------------------------------------------
1. Ensure Tailscale is running and connected:
     tailscale up
2. Double-click "Start-Server.bat".
3. The launcher will:
   - Verify Java 17+ is installed.
   - Detect your machine's Tailscale IPv4 address (100.x.x.x).
   - Create the "data/" directory if needed.
   - Launch the Spring Boot server with SQLite database persistence.
4. Note the displayed server address (e.g., http://100.x.x.x:8080).
5. Share this IP address with the 3 client users so they can set it in their
   client-config.ini file.

--------------------------------------------------------------------------------
3. STOPPING THE SERVER
--------------------------------------------------------------------------------
- Press CTRL+C in the terminal window.
- The server will perform a graceful shutdown.
- All messages and user data in "data/chat.db" are preserved.
- Do NOT close the window by force (clicking X) if you want cleanly flushed WAL logs,
  though SQLite WAL mode ensures crash resilience regardless.

--------------------------------------------------------------------------------
4. CONFIGURATION
--------------------------------------------------------------------------------
Edit "server-config.properties":
  SERVER_PORT=8080

If you change the port, ensure all clients update their client-config.ini.

--------------------------------------------------------------------------------
5. DATABASE & BACKUPS
--------------------------------------------------------------------------------
- SQLite Database: data/chat.db
- WAL mode is active for optimal concurrent reads and durability.
- To create a backup:
  Copy "data/chat.db" into the "backups/" folder:
    copy data\chat.db backups\chat-backup-%DATE:~10,4%%DATE:~4,2%%DATE:~7,2%.db

--------------------------------------------------------------------------------
6. USER ACCOUNTS & PASSWORD MANAGEMENT
--------------------------------------------------------------------------------
Four accounts are configured in the database:
  jyoshna (Host laptop user), meghana (Client 1), yash (Client 2), yashuu (Client 3)

To assign individual passwords securely before deployment:
  Double-click "Set-Password.bat" or run:
    Set-Password.bat jyoshna
    Set-Password.bat meghana
    Set-Password.bat yash
    Set-Password.bat yashuu

The utility prompts for the password, securely hashes it with BCrypt (cost 12),
updates the database directly, and exits. Passwords are never logged or stored
in plaintext. Do NOT distribute default shared passwords across users.

--------------------------------------------------------------------------------
7. WINDOWS FIREWALL
--------------------------------------------------------------------------------
If client machines on your Tailscale network cannot connect, allow port 8080:
Run PowerShell as Administrator:
  New-NetFirewallRule -DisplayName "Private Chat Server" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8080 -RemoteAddress "100.64.0.0/10"
================================================================================

