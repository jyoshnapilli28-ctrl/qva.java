================================================================================
                         QVA WINDOWS HOST — QUICK START
================================================================================

PREREQUISITES
- Windows 11, Java 17+, and Tailscale installed and connected.
- The approved QVA release package and the existing HOST account credentials.
- Do not initialize a fresh production database over an existing one.

START
1. Keep the existing data\chat.db and make a verified backup before upgrading.
2. Double-click Start-Server.bat. The service binds only to 127.0.0.1.
3. In an elevated or normal terminal as supported by Tailscale, run:
       tailscale serve --bg 8080
   Follow the displayed private HTTPS URL (the HOST's *.ts.net name) for member
   configuration. Verify with `tailscale serve status`. Do not run `tailscale funnel`.
4. Keep Windows Defender Firewall enabled. Do not add a public inbound rule, open
   the router, or allow the whole Tailscale CGNAT range to reach port 8080. The
   backend listens on loopback, so Tailscale Serve is the only remote app path.
5. Open the private URL locally, sign in with the existing HOST account, create
   invitations, and share the host machine with each external member separately.

STOP / RESTART
- Press Ctrl+C in the server console and wait for the process to stop.
- Start-Server.bat again. Verify `tailscale serve status` still shows private
  Serve for port 8080; reapply `tailscale serve --bg 8080` if necessary.
- To remove Serve, run `tailscale serve reset` only when intentionally disabling
  member access. Never enable Funnel.

BACKUP / RESTORE
- Back up the complete data directory while QVA is stopped, including chat.db and
  any SQLite WAL/SHM files. Alternatively use SQLite's online backup API/tool.
- Store encrypted copies in a separate protected location and verify a restore on
  a disposable copy. Never test restore against the live HOST database.
- Restore only while QVA is stopped, preserving file permissions, then start and
  verify application health and expected accounts. Never delete or recreate the
  production database as a troubleshooting step.

SLEEP AND STARTUP
- In Windows Settings > System > Power, set sleep to Never while plugged in for
  periods when QVA must be available. Keep normal screen lock enabled.
- Add a startup task only after manual acceptance of the release. Run it under the
  HOST Windows account, with a logged, restartable process; do not store QVA
  passwords in the task command line. Confirm private Serve and health after reboot.

MACHINE SHARING
- Share only this HOST machine with each intended external Tailscale account.
- Review current Tailscale ACL/grant policy before adding or changing anything.
  Restrict shared access to the QVA service port using Tailscale grants where the
  current share/ACL model supports it, and retain Windows Defender Firewall.
- Machine sharing alone does not guarantee app-only access. Validate from one
  external test account before inviting the full group. The current environment
  did not perform that external account test.
================================================================================
