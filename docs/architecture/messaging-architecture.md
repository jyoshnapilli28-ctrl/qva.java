# Direct conversation development relay

This is the accepted M5 baseline. The current additions and retention policy are in [M5.2 lifecycle](m5-2-message-lifecycle.md).

Availability: authenticated status endpoint and Messages shell. The relay requires explicit `--qva.messaging.enabled=true` on a disposable development server. Production defaults to disabled.

| Endpoint | Result |
| --- | --- |
| GET /api/v2/conversations/status | Shows whether the development relay is enabled. |
| GET /api/v2/conversations | Latest 100 direct conversations for the current approved device's account. |
| POST /api/v2/conversations/direct | Opens a direct conversation from `{"peerId":123}`; the same unordered pair resolves to the same conversation. |
| GET /api/v2/conversations/{id}/envelopes?before=123 | Latest 50 envelopes below the optional sequence cursor, newest first. The JSON byte field is base64. |
| POST /api/v2/conversations/{id}/envelopes | Submits bounded binary input with a UUID Idempotency-Key and QVA-Envelope-Version 1. Retries with the same device, request ID and payload return the first accepted row. Conflicting reuse returns 409. |

v2 CSRF applies to writes. Origin and session policy follow existing v2 security. Sender account and device come from the authenticated approved device binding. Neither roles nor IDs in client input grant access. The development relay stores up to 16 KiB per envelope. The envelope schema has no plaintext field. Binary acceptance does not prove encryption. Use synthetic bytes for development. Real-user messaging needs the reviewed client and protocol.

Offline history uses the same durable, ordered retrieval API. There is no automatic delivery or read status. A persisted envelope is an accepted envelope, not an acknowledgement from a recipient. Group membership, push notifications, receipts, selectors for decrypted message history and retention quotas require further work. No automatic expiry or purge is implemented. Do not run the fixture relay with personal data.
