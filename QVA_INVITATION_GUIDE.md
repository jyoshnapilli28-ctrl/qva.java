# QVA Invitation Guide

## HOST

1. Sign in through the existing HOST account on its approved device.
2. In Host Administration, create an invitation and choose an expiry (24 hours, 7 days or 30 days).
3. Copy the private registration link immediately. The raw token is shown once and is not retrievable later; the database stores only its SHA-256 hash.
4. Send it privately to one intended member. Share the HOST machine to that person's own Tailscale account separately.
5. Review the invitation list and audit. Revoke an unused link if it was sent to the wrong person or is no longer needed.
6. Separately approve or reject the new member's pending device. Registration does not approve devices.

Limits: at most 20 active invitations; tokens are single-use and expire; registration is throttled to five attempts per 15-minute window per observed client identity/address in the current server process. The throttle is in-memory and resets on restart. No public endpoint grants HOST role.

## Member

Open the private invitation link, choose an individual username and password, and create the account. The account defaults to MEMBER. Then sign in, register a device and wait for HOST approval.

## Current coverage

Invitation migration and endpoints were source-implemented in this freeze, and an integration test was added. The backend was unavailable in this environment, so invitation lifecycle tests have not been executed. Do not use invitations for production onboarding until the release verification passes the Spring integration test and packaged registration flow.
