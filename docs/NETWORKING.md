# Networking Guide

## Architecture

This system uses **Tailscale** as the private networking layer.

Tailscale creates an encrypted peer-to-peer mesh network (based on WireGuard) between
all four machines. Each device gets a Tailscale IP in the `100.64.0.0/10` range.

No port forwarding, no static public IPs, no VPN server to maintain.

```
                    TAILSCALE MESH (encrypted WireGuard)
                              │
           ┌──────────────────┼──────────────────┐
           │                  │                  │
     100.64.0.1         100.64.0.2         100.64.0.3
     SERVER              CLIENT 1           CLIENT 2
     Java :8080          Browser            Browser
                                                │
                                          100.64.0.4
                                           CLIENT 3
                                           Browser
```

## Setup: All Four Machines

### 1. Install Tailscale

Download from: https://tailscale.com/download/windows

Install and sign in to the **same Tailscale account** on all four machines.
Use a free personal account — supports up to 100 devices.

### 2. Connect All Devices

On each machine, run:
```
tailscale up
```

Sign in when prompted. All four machines must appear in your Tailscale admin panel:
https://login.tailscale.com/admin/machines

### 3. Find the Server's Tailscale IP

On the **server machine**:
```cmd
tailscale ip -4
```

This outputs something like: `100.64.0.1`

Share this IP with the other three members. They put it in `client-config.ini`:
```ini
SERVER_URL=http://100.64.0.1:8080
```

## Windows Firewall Configuration

The server machine's Windows Firewall must allow inbound connections on port 8080
**from the Tailscale network**.

### Option A: Allow via PowerShell (recommended)

Run on the **server machine** as Administrator:

```powershell
New-NetFirewallRule `
  -DisplayName "Private Chat Server" `
  -Direction Inbound `
  -Action Allow `
  -Protocol TCP `
  -LocalPort 8080 `
  -RemoteAddress "100.64.0.0/10"
```

The `100.64.0.0/10` range covers all Tailscale IPs.

### Option B: Allow via Windows Defender Firewall UI

1. Open **Windows Defender Firewall with Advanced Security**.
2. Click **Inbound Rules** → **New Rule**.
3. Select **Port** → Next.
4. Select **TCP**, specific port: `8080` → Next.
5. Allow the connection → Next.
6. Check **Domain** and **Private** (uncheck **Public**) → Next.
7. Name: `Private Chat Server` → Finish.

### Verifying Connectivity

From a client machine:
```cmd
curl http://100.64.0.1:8080/api/auth/status
```

Expected response: `{"status":"ok","service":"private-chat"}`

## MagicDNS (Optional)

If MagicDNS is enabled on your Tailnet, you can use the machine's hostname instead of IP:

```ini
SERVER_URL=http://server-hostname:8080
```

Enable MagicDNS at: https://login.tailscale.com/admin/dns

## Security Notes

- The chat server is only accessible within the Tailscale network.
- Tailscale traffic is encrypted end-to-end with WireGuard.
- The server does **not** expose itself on your LAN or the public internet (when bound correctly).
- All messages require authenticated sessions (username + BCrypt password).
- Sessions are cookie-based with `HttpOnly` and `SameSite=Strict` flags.

## Troubleshooting

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| Browser shows "connection refused" | Server not running | Start `Start-Server.bat` |
| Browser shows "connection timed out" | Firewall blocking | Run the firewall command above |
| Client can't reach server | Tailscale not connected | Run `tailscale up` |
| Wrong IP in config | Misconfigured client | Update `client-config.ini` |
| Port 8080 already in use | Another app on the port | Change `SERVER_PORT=8081` in `server-config.properties` |

