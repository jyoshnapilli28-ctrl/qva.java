# Client Setup & User Guide

This guide describes how to configure and use the Private Chat client on Windows laptops.

---

## 1. System Requirements

- **Operating System:** Windows 10 or Windows 11 (64-bit)
- **VPN:** Tailscale installed and signed in
- **Web Browser:** Any modern browser (Microsoft Edge, Google Chrome, Mozilla Firefox)
- **Zero Dependencies:** No Java, Maven, Python, Docker, or Node.js required!

---

## 2. Initial Setup (One Time)

1. Receive `Private-Chat-Client.zip` from the server administrator.
2. Extract the ZIP file to any folder on your laptop (e.g., `C:\PrivateChat\` or your Desktop).
3. Ensure Tailscale is running and connected:
   - Check the Tailscale icon in your Windows system tray.
   - Or open command prompt and run:
     ```cmd
     tailscale status
     ```
4. Ask the server administrator for the Server's Tailscale IPv4 address (e.g., `100.x.x.x`).
5. Open `client-config.ini` in Notepad:
   ```ini
   SERVER_URL=http://100.x.x.x:8080
   ```
   Replace `ENTER-SERVER-TAILSCALE-IP` with the real IP address. Save the file.

---

## 3. Joining the Chat

1. Double-click **`Start-Client.bat`**.
2. The launcher performs automated pre-checks:
   - Verifies Tailscale presence
   - Verifies `SERVER_URL` in `client-config.ini`
   - Performs a lightweight HTTP connectivity check to `/api/auth/status`
   - Automatically opens your default web browser to the chat room.
3. On the login screen:
   - Enter your assigned username (`jyoshna`, `meghana`, `yash`, or `yashuu`).
   - Enter your unique password assigned by the host operator.
   - Click **Sign In** (or press Enter).
4. The chat interface loads:
   - Left sidebar displays the group members and dynamic Online / Offline badges.
   - Message area loads recent chat history from the central server.
   - The top header shows connection status (`CONNECTED`).

---

## 4. Chat Features

- **Real-Time Messaging:** Messages appear instantly across all connected laptops using WebSocket/STOMP.
- **Enter-to-Send:** Type a message and press Enter to send immediately.
- **Timestamps:** Rendered automatically in your local timezone.
- **Presence Indicators:** See when group members connect or disconnect in real time.
- **Auto-Reconnection:** If your network drops or your laptop sleeps, the client automatically attempts reconnection with exponential backoff (2s up to 30s) and synchronizes missing message history as soon as connection is restored.
- **Session Persistence:** Closing and reopening your browser keeps your authenticated session active for up to 24 hours.

---

## 5. Troubleshooting

| Problem | Cause | Solution |
|---|---|---|
| Browser displays `ERR_CONNECTION_REFUSED` | The chat server is stopped | Contact the server operator to run `Start-Server.bat` |
| Launcher warns `Cannot reach http://...` | Tailscale is offline or IP is wrong | Verify `tailscale status` and check IP in `client-config.ini` |
| "Invalid username or password" | Wrong credentials | Check lowercase username and password caps lock |
| Chat says `DISCONNECTED` | Server restarted or Wi-Fi dropped | The client will auto-reconnect. You can also press F5 to reload |

