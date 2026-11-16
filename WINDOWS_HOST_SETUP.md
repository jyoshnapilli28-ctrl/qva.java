# QVA Windows HOST Setup

This guide targets a single Windows 11 HOST running the QVA Spring Boot server and SQLite database. The application binds to `127.0.0.1`; remote members reach it through private Tailscale Serve HTTPS.

## Before upgrade

1. Confirm you have the approved release package and can sign in with the existing HOST account.
2. Stop the existing QVA process cleanly.
3. Make a verified copy of the complete `data` directory. Preserve the original `chat.db`; do not initialize over it or delete it to troubleshoot.
4. Keep Windows Defender Firewall enabled. Do not configure router port forwarding, Tailscale Funnel, Cloudflare Tunnel, or a public reverse proxy.

## Start QVA

1. Install and connect Tailscale on the HOST Windows account.
2. Start `Start-Server.bat` from the server package. It checks Java 17+ and launches the packaged JAR with `server.address=127.0.0.1` and the production profile.
3. In a terminal, enable private Tailscale Serve for the configured app port (default 8080):

   ```powershell
   tailscale serve --bg 8080
   tailscale serve status
   ```

4. Use the private HTTPS `*.ts.net` name shown by Serve as the member-facing address. Confirm the Serve mode is private. Never enable Funnel.
5. Open the private HTTPS URL and sign in with the existing HOST credentials. Device approval and HOST administration still require the approved QVA HOST device.

The Spring app should not listen on the Tailscale interface or on `0.0.0.0`. There must be no inbound firewall rule for app port 8080.

## Windows Defender Firewall

Check that firewall profiles are enabled:

```powershell
Get-NetFirewallProfile | Select-Object Name, Enabled, DefaultInboundAction
Get-NetAdapter | Where-Object { $_.InterfaceDescription -like '*Tailscale*' } | Select-Object Name, InterfaceDescription, Status
```

Keep inbound traffic blocked by default. If Windows requires an explicit HTTPS rule for Tailscale Serve, create one only after identifying the actual Tailscale adapter alias. Limit it to TCP 443, that adapter, and Tailscale source addresses; review Tailscale ACL/grants for per-user and per-port access as well. `100.64.0.0/10` is a broad Tailscale address range, not an individual-user rule, so do not treat it as the sole authorization boundary.

Example, replacing `Tailscale` with the adapter alias returned above:

```powershell
New-NetFirewallRule -DisplayName 'QVA Tailscale Serve HTTPS' `
  -Direction Inbound -Action Allow -Protocol TCP -LocalPort 443 `
  -InterfaceAlias 'Tailscale' -RemoteAddress '100.64.0.0/10' -Profile Any
```

Before creating a rule, inspect existing policy and avoid duplicate or broader rules. Do not add TCP 8080 as an inbound exception. Validate actual access from an external share account and verify that other ports remain unavailable.

## Stable address and sharing

Use the private Serve `*.ts.net` hostname. Share the HOST machine with each intended external Tailscale account individually. Members should use their own Tailscale account; they do not need to join the HOST's tailnet. Review the current tailnet policy before changing it. Machine sharing only supplies network reachability; QVA invitation redemption and device approval are separate.

Tailscale references: [machine sharing](https://tailscale.com/kb/1084/sharing/), [Serve](https://tailscale.com/kb/1312/serve/), and [access controls](https://tailscale.com/kb/1018/acls/).

## Startup, sleep and restart

- Keep the HOST plugged in and set Windows sleep to Never during intended availability windows; retain screen lock and normal account protections.
- For manual operation, keep the server console open. Press Ctrl+C and wait for graceful shutdown before restart.
- For unattended operation, configure a Windows Task Scheduler task under the HOST account only after manual release verification. Run it only when the HOST user is logged on so the console and recovery path are visible. Do not put QVA passwords in task arguments. Test after a reboot and verify QVA health plus `tailscale serve status`.
- If Serve is missing after restart, reapply `tailscale serve --bg 8080`; verify it remains private. Use `tailscale serve reset` only when intentionally removing access.

## Backup and restore

- Preferred: stop QVA, copy the complete `data` directory (including any SQLite `-wal`/`-shm` files), and store an encrypted backup separately.
- Alternatively use SQLite's online backup API while the service is active. Do not copy only `chat.db` from a live WAL database.
- Periodically restore a backup to a disposable directory and verify it with a non-production copy of the app. Never point a restore test at the HOST's live database.
- To recover, stop QVA, preserve the current data directory, restore the verified backup to the expected path, start QVA, and check the expected accounts and app health. Do not reset accounts or create a fresh production database as a recovery shortcut.
