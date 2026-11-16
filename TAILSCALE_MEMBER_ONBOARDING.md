# Tailscale Member Onboarding

Each member uses their own Tailscale account and their own QVA account. Tailscale machine sharing does not create QVA credentials, approve a device, or grant HOST privileges.

1. Install Tailscale on your device and sign in to your own account.
2. Accept the individual machine-share invitation sent by the QVA HOST. The HOST shares only the intended QVA machine; you do not need to join the HOST's tailnet.
3. Confirm the shared HOST machine appears online in Tailscale.
4. Set `SERVER_URL` in `client-config.ini` to the exact private HTTPS `*.ts.net` address supplied by the HOST. Do not use a raw `100.x` URL, a public tunnel, or Funnel.
5. Open the QVA URL, choose **Create an account**, and redeem your private QVA invitation using a unique username and a long password.
6. Sign in and register this browser/device. New devices start PENDING.
7. Wait for the QVA HOST to approve the device. Ask the HOST if it remains pending.
8. Use only conversations you are authorized to access. Report lost devices to the HOST promptly so the device can be revoked.

If the invitation is expired, used, or revoked, ask the HOST for a fresh one. Do not forward an invitation link. Previously copied messages or keys cannot be remotely erased when an account/device is revoked.
