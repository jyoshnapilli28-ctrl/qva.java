# QVA M5.2 private transport checkpoint

This change extends the accepted V5 implementation on `feature/qva-2.0`. It leaves migrations V1–V5 and the classic chat paths intact. V6 adds group records, participant membership, group envelopes, a read-only SQL view across both envelope stores, receipts, cleanup indexes and retry tombstones. Direct conversations still use the V5 tables and identifiers.

## Delivery and access

The Messages page selects and sends predefined synthetic fixtures as binary envelopes. It displays known fixture labels and opaque metadata. There is no free-text message composer or plaintext message preview. Group names and membership are routing metadata visible to the server. Envelope format 1 describes the development transport only. The relay defaults to disabled and should be enabled only in a separate disposable fixture run. Binary input is not cryptographic verification.

Every conversation route uses the authenticated principal, approved current device and live workspace session. Sender identity is derived on the server. Mutations use v2 CSRF protection. History and receipt operations check membership, including the group join sequence. A HOST may create a group, but ownership of a specific group is required to change participants. A HOST who is not a participant cannot access a conversation. Removing a participant prevents future retrieval and new submissions. A later rejoin begins a new history window. Revocation cannot erase copies that were already delivered.

The selected conversation uses sequential, authenticated REST reconciliation about once per second, with an eight-second request timeout and visible retry state. This is the scoped live delivery alternative to a dedicated push channel. It avoids an extra socket authorization lifetime and keeps message data off the operations WebSocket. It assumes a small number of active clients and has poll latency. Only an open selected conversation polls for messages. Open the conversation after reconnect to retrieve retained offline history. Initial and backward pages use descending sequence cursors; catch-up after a seen sequence is ascending so backlog pages do not skip older new messages. The browser deduplicates by sequence within a conversation.

A received envelope triggers a delivery receipt. An explicit Mark read action advances that recipient account's receipt. The database preserves the first delivery and read timestamp. Receipt writes are approved-device and membership checked, monotonic and idempotent. The sender can look up receipts for their own envelope; a recipient can look up their own receipt. Receipt inspection is live while open. Device-scoped idempotency keys are unique across the direct and group relay paths. A conflicting retry returns an error. The UI retains a failed send's key for a retry in the same view.

## Retention

History hides messages older than 30 days. Hourly cleanup, while the fixture relay is enabled, deletes expired envelopes and their receipts transactionally. It preserves device/request tombstones for seven more days after cleanup so a delayed retry cannot recreate an expired message during that window. Conversation and membership metadata are retained. Cleanup does not erase browser copies or backups. The v1 message store is outside this policy.

## API delta

All paths below are relative to `/api/v2/conversations`.

| Path and method | Function |
| --- | --- |
| GET /groups | List the current account’s groups. |
| POST /groups | Create a named group from an approved HOST device. |
| GET /groups/{id}/participants | List participants as a current member. |
| POST /groups/{id}/participants | Owner adds `{"userId":…}` with no access to pre-join history. |
| DELETE /groups/{id}/participants/{userId} | Owner removes a participant. |
| GET /{id}/envelopes | Retrieve up to 50 retained envelopes with optional `before` or `after` sequence cursors. |
| POST /{id}/envelopes | Existing binary submit contract, now with group membership and cross-type replay checks. |
| POST /{id}/envelopes/{sequence}/receipt | Recipient records `DELIVERED` or `READ`. |
| GET /{id}/envelopes/{sequence}/receipt | Return receipt state visible to the caller. |

## Release boundaries

The verified lifecycle is a synthetic development transport. A reviewed E2EE protocol and client, keys and recovery, Windows evidence, and four physical devices remain separate gates. The existing one-session-per-account policy remains unchanged. The background cleanup requires a running, enabled fixture relay. This checkpoint is not production enablement.
