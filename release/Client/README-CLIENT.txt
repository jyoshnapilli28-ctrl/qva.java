================================================================================
                         QVA MEMBER CLIENT — QUICK START
================================================================================

1. Install Tailscale on your device and sign in with your own Tailscale account.
2. Accept the QVA HOST machine-share invitation from the host operator.
3. Confirm that Tailscale shows the shared host as available.
4. Edit client-config.ini and set SERVER_URL to the exact private HTTPS *.ts.net
   address provided by the host. The host must have Tailscale Serve enabled.
5. Double-click Start-Client.bat. QVA will open in your browser.
6. Open /register or select Create an account. Enter your private QVA invitation,
   choose your own username and password, and create your QVA account.
7. Register this device in QVA. It remains pending until the QVA HOST approves it.
8. After approval, sign in with your own QVA credentials and use only conversations
   for which your account is a member.

Tailscale machine sharing grants network reachability only. It does not create a
QVA account, approve a device, or grant HOST privileges. Do not share your QVA
invitation with anyone else. If the invitation expires or is revoked, ask the
HOST for a new one.

Connection failures: confirm Tailscale is connected, the host is online, and the
configured URL is the host's Tailscale Serve HTTPS name. Never use a public tunnel
or ask the host to enable Tailscale Funnel or router port forwarding.
================================================================================
