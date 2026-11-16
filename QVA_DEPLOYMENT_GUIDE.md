> **Superseded deployment guide:** For the current QVA 2.0 freeze, follow [WINDOWS_HOST_SETUP.md](WINDOWS_HOST_SETUP.md), [TAILSCALE_MEMBER_ONBOARDING.md](TAILSCALE_MEMBER_ONBOARDING.md), and [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md). Older IP and tunnel instructions below are historical and must not be used for deployment.

# QVA — FOUR-DEVICE PRODUCTION DEPLOYMENT GUIDE

**Project:** QVA — Private Java-Based 4-Device Chat Application  
**Target Roles:** 1 Host Server Laptop (`jyoshna`) + 3 Remote Client Laptops (`meghana`, `yash`, `yashuu`)  
**Deployment Target Date:** TODAY  
**Package Locations:**  
- Server Package: `release\Server\`  
- Client Distributable: `release\Private-Chat-Client.zip`  

---

## 1. ARCHITECTURE OVERVIEW

```text
               +-------------------------------------------+
               |        HOST LAPTOP (User: jyoshna)        |
               |  - Windows 10/11 + Java 17                |
               |  - Spring Boot Server (Port 8080)         |
               |  - Persistent SQLite Database (chat.db)   |
               |  - Browser UI: http://localhost:8080/     |
               +-------------------------------------------+
                                     |
               +---------------------+---------------------+
               |                     |                     |
     (Tailscale VPN Mesh)  (Tailscale VPN Mesh)  (Tailscale VPN Mesh)
               |                     |                     |
               v                     v                     v
    +--------------------+  +--------------------+  +--------------------+
    |  CLIENT LAPTOP 1   |  |  CLIENT LAPTOP 2   |  |  CLIENT LAPTOP 3   |
    |  (User: meghana)   |  |  (User: yash)      |  |  (User: yashuu)    |
    |  - Windows 10/11   |  |  - Windows 10/11   |  |  - Windows 10/11   |
    |  - Zero Java/Node  |  |  - Zero Java/Node  |  |  - Zero Java/Node  |
    |  - Start-Client.bat|  |  - Start-Client.bat|  |  - Start-Client.bat|
    +--------------------+  +--------------------+  +--------------------+
```

---

## 2. HOST LAPTOP SETUP (`jyoshna`)

### Step 2.1: Verify Java 17
On the host laptop, open Command Prompt and verify:
```cmd
java -version
```
Expected output: `openjdk version "17.x.x"` or Oracle Java 17.

---

### Step 2.2: Allow Port 8080 in Windows Firewall
To allow other laptops on the network or Tailscale mesh to connect to port 8080:
Open Command Prompt **as Administrator** on the host laptop and run:
```cmd
netsh advfirewall firewall add rule name="QVA Chat Server" dir=in action=allow protocol=TCP localport=8080
```

---

### Step 2.3: Initialize Client Passwords
Before distributing client packages, initialize the unique passwords for the 3 clients.  
Open Command Prompt in `release\Server\` on the host laptop:

```cmd
cd /d C:\Users\JOSHU\qva.deploy\release\Server

Set-Password.bat meghana
Set-Password.bat yash
Set-Password.bat yashuu
```

**Password Requirements:**
- At least 12 characters
- At least one uppercase letter (`A-Z`)
- At least one lowercase letter (`a-z`)
- At least one digit (`0-9`)
- At least one special symbol (`!@#$%^&*...`)

*(Example pattern: `Meghana@Qva2026!`, `Yash@Qva2026!`, `Yashuu@Qva2026!`)*

> [!NOTE]
> Do NOT run `Set-Password.bat jyoshna`. Account `jyoshna` already has an active, validated password initialized in `chat.db`.

---

### Step 2.4: Start the Server
On the host laptop:
1. Navigate to `release\Server\`.
2. Double-click **`Start-Server.bat`**.
3. The server terminal will start Spring Boot, apply SQLite pragmas, and display:
   ```text
   Started PrivateChatApplication in X.X seconds
   ```
4. On the host laptop, open your web browser to:
   ```text
   http://localhost:8080/
   ```
5. Log in with username **`jyoshna`** and your password.

---

## 3. NETWORK CONFIGURATION

### Method A: Tailscale VPN (Recommended for College Wi-Fi)

Tailscale provides an encrypted WireGuard peer-to-peer network that automatically traverses institutional firewalls.

1. **Install Tailscale** on all 4 laptops:
   - Download free from: [https://tailscale.com/download/windows](https://tailscale.com/download/windows)
2. **Sign In:** Sign all 4 laptops into the same Tailscale account (e.g. using a shared Google or GitHub login) or invite them to the same Tailnet.
3. **Find Host IP:** On the host laptop, open Command Prompt and run:
   ```cmd
   tailscale ip -4
   ```
   Note the assigned 100.x.x.x address (for example: `100.82.14.35`).
4. Share this address with Meghana, Yash, and Yashuu.

---

### Method B: Cloudflare Tunnel (Alternative via Mobile Hotspot)

If the host laptop is connected to a personal mobile hotspot (where port 7844 is unrestricted):
1. In Command Prompt, run:
   ```cmd
   cloudflared tunnel --url http://127.0.0.1:8080
   ```
2. Note the generated public URL:
   ```text
   https://<subdomain>.trycloudflare.com
   ```
3. Share this HTTPS link with the clients.

---

## 4. CLIENT LAPTOP SETUP (`meghana`, `yash`, `yashuu`)

Each client laptop receives the standalone client package: **`release\Private-Chat-Client.zip`** (via USB drive, email, or local file share).

### Step 4.1: Extract Package
1. Copy `Private-Chat-Client.zip` to the client laptop.
2. Right-click and choose **Extract All...** to a folder (e.g., `Desktop\Private-Chat-Client`).
3. The folder contains 3 files:
   - `Start-Client.bat`
   - `client-config.ini`
   - `README-CLIENT.txt`

---

### Step 4.2: Configure Server URL
1. Open **`client-config.ini`** in Notepad.
2. Edit the `SERVER_URL` line:
   - For Tailscale VPN:
     ```ini
     SERVER_URL=http://100.82.14.35:8080
     ```
     *(Replace `100.82.14.35` with the host laptop's actual Tailscale IP)*
   - For Cloudflare Tunnel:
     ```ini
     SERVER_URL=https://your-subdomain.trycloudflare.com
     ```
3. Save and close the file.

---

### Step 4.3: Launch & Log In
1. Double-click **`Start-Client.bat`**.
2. The batch launcher verifies the server URL and opens the chat UI in the client's default browser.
3. Sign in:
   - Client 1: Username `meghana` + password assigned by host
   - Client 2: Username `yash` + password assigned by host
   - Client 3: Username `yashuu` + password assigned by host
4. You are now connected to the private group chat room `#general`.

---

## 5. POST-DEPLOYMENT VERIFICATION CHECKLIST

- [ ] Host laptop has `Start-Server.bat` running in an open console window.
- [ ] Host operator can log into `http://localhost:8080/` as `jyoshna`.
- [ ] Client 1 can access chat and log in as `meghana`.
- [ ] Client 2 can access chat and log in as `yash`.
- [ ] Client 3 can access chat and log in as `yashuu`.
- [ ] Messages typed on any device appear in real-time across all other 3 screens.
- [ ] Online presence indicator shows all 4 users active.
- [ ] Refreshing the browser preserves past message history.

