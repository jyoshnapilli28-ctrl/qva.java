================================================================================
                    PRIVATE CHAT CLIENT — USER GUIDE
================================================================================

Welcome to the Private Group Chat!

You do NOT need Java, Maven, Docker, or any development tools installed.
All you need is Windows and your standard web browser.

--------------------------------------------------------------------------------
1. PREREQUISITES
--------------------------------------------------------------------------------
- Windows 10 or 11
- Web Browser: Google Chrome, Microsoft Edge, Mozilla Firefox, or Brave
- Network connection:
    * If connecting via Tailscale VPN: Tailscale connected to the private group
    * If connecting via Cloudflare Tunnel: Any standard internet connection

--------------------------------------------------------------------------------
2. SETUP (FIRST TIME ONLY)
--------------------------------------------------------------------------------
1. Ask the server operator (jyoshna) for the SERVER_URL:
   - For Tailscale VPN: http://100.x.x.x:8080
   - For Cloudflare Tunnel: https://<subdomain>.trycloudflare.com
2. Open "client-config.ini" in Notepad.
3. Edit the line:
     SERVER_URL=http://ENTER-SERVER-ADDRESS:8080
   Replace it with the actual address, for example:
     SERVER_URL=http://100.64.0.1:8080
   or:
     SERVER_URL=https://guam-way-aids-seven.trycloudflare.com
4. Save and close the file.

--------------------------------------------------------------------------------
3. LAUNCHING CHAT
--------------------------------------------------------------------------------
1. Double-click "Start-Client.bat".
2. The launcher will:
   - Read the server URL from client-config.ini.
   - Check network configuration (Tailscale or Cloudflare Tunnel).
   - Verify connection to the server.
   - Automatically open the chat room in your default browser.
3. Sign in with your assigned username and password:
     Usernames: jyoshna (Host), meghana, yash, yashuu
     (Enter the unique password assigned to your account by the host operator).
4. Start chatting! Enter messages in the input box and press Enter or click Send.

--------------------------------------------------------------------------------
4. TROUBLESHOOTING
--------------------------------------------------------------------------------
- "Cannot reach server":
   * Confirm the server machine has "Start-Server.bat" running.
   * If using Tailscale, ensure Tailscale is connected: run "tailscale status".
   * Check that the URL in "client-config.ini" exactly matches the host's URL.
   * If using direct Tailscale, confirm Windows Firewall allows port 8080 on host.

- "Invalid username or password":
   * Verify you are using your designated username in all lowercase.
   * Check Caps Lock when typing your password.
   * If this is your first time logging in, ask the host operator to run:
       Set-Password.bat <your-username>
     on the server laptop to initialize your credentials.
================================================================================
