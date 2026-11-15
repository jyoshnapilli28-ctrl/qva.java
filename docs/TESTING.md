# Testing Report & Multi-Device Acceptance Checklist

This document details the automated test suite results and provides an explicit manual verification procedure for deployment across 4 physical Windows laptops over Tailscale.

---

## 1. Automated Test Suite Summary

All tests are executed via `mvn clean test` against embedded and isolated test environments.

- **Test Classes:** 3
- **Total Tests Executed:** 35
- **Passed:** 35
- **Failures:** 0
- **Errors:** 0
- **Skipped:** 0
- **Build Status:** SUCCESS

### Detailed Breakdown

#### A. Database & Messaging Integration (`PrivateChatIntegrationTest` — 16 tests)
1. `testFourUsersExist`: Verifies `alice`, `bob`, `carol`, `dave` accounts exist and are active.
2. `testUserPasswordHashesAreStored`: Verifies all 4 user accounts possess valid BCrypt hashes corresponding to `chat1234`.
3. `testUsernamesAreUnique`: Verifies uniqueness constraints on the username column.
4. `testGeneralRoomExists`: Verifies seeded chat room `general`.
5. `testMessagePersistence`: Verifies message counts increment in SQLite upon sending.
6. `testMessageContentIsStored`: Verifies message text round-trips accurately.
7. `testMessageHasTimestamp`: Verifies UTC timestamp generation and storage.
8. `testMessageSenderIdentity`: Verifies sender display name and username association.
9. `testHistoryIsOrderedChronologically`: Verifies messages are returned in chronological order.
10. `testEmptyMessageRejected`: Verifies empty string validation.
11. `testBlankMessageRejected`: Verifies whitespace-only validation.
12. `testMessageTooLongRejected`: Verifies rejection of messages exceeding 2000 characters.
13. `testUnknownRoomRejected`: Verifies error handling when addressing non-existent rooms.
14. `testUnknownUserRejected`: Verifies error handling for unknown sender identity.
15. `testHistoryLimitRespected`: Verifies query limits (e.g., retrieving only 3 messages).
16. `testMultipleUserMessagesInHistory`: Verifies interleaving messages from multiple senders.

#### B. REST API & Endpoint Security (`RestApiTest` — 11 tests)
1. `authStatusPublic`: Verifies unauthenticated `GET /api/auth/status` returns HTTP 200 `{"status":"ok"}`.
2. `authMeRequiresAuthentication`: Verifies `GET /api/auth/me` returns HTTP 401 when unauthenticated.
3. `authMeReturnsUser`: Verifies authenticated `GET /api/auth/me` returns username and authentication status.
4. `usersRequiresAuthentication`: Verifies `GET /api/users` returns HTTP 401 when unauthenticated.
5. `usersListReturnsFourUsers`: Verifies authenticated `GET /api/users` returns all 4 members with presence data.
6. `messagesRequiresAuthentication`: Verifies `GET /api/rooms/general/messages` returns HTTP 401 when unauthenticated.
7. `messagesHistoryReturnsOk`: Verifies authenticated message history retrieval.
8. `unknownRoomReturns400`: Verifies `GET /api/rooms/doesnotexist/messages` returns HTTP 400 Bad Request.
9. `healthEndpointReturnsUp`: Verifies Actuator/Health endpoint returns `{"status":"UP"}`.
10. `indexHtmlIsPublic`: Verifies static `index.html` is accessible without credentials.
11. `rootRedirectsToIndex`: Verifies root path `/` serves the chat UI.

#### C. WebSocket & STOMP Security (`WebSocketSecurityTest` — 8 tests)
1. `testUnauthenticatedSubscribeToMessagesRejected`: Verifies unauthenticated SUBSCRIBE to `/topic/messages/general` throws `AccessDeniedException`.
2. `testUnauthenticatedSubscribeToPresenceRejected`: Verifies unauthenticated SUBSCRIBE to `/topic/presence` throws `AccessDeniedException`.
3. `testAuthenticatedSubscribeToMessagesAllowed`: Verifies authenticated SUBSCRIBE to message topics is permitted.
4. `testAuthenticatedSubscribeToPresenceAllowed`: Verifies authenticated SUBSCRIBE to presence topics is permitted.
5. `testUnauthenticatedSendRejected`: Verifies unauthenticated SEND to `/app/chat.send` throws `AccessDeniedException`.
6. `testAuthenticatedSendToApprovedDestinationAllowed`: Verifies authenticated user can send to `/app/chat.send`.
7. `testAuthenticatedSendToDirectTopicRejected`: Verifies sending directly to `/topic/messages/general` is rejected.
8. `testAuthenticatedSendToUnapprovedAppDestinationRejected`: Verifies sending to unapproved destination `/app/admin.shutdown` is rejected.

---

## 2. Multi-Device Manual Acceptance Checklist

Because physical execution across four separate physical laptops requires real hardware on your Tailnet, use this explicit checklist for live validation:

### Phase 1: Environment Setup
- [ ] **Server Laptop:** Tailscale connected (`tailscale ip -4` records IP `100.A.B.C`).
- [ ] **Client 1:** Tailscale connected on group Tailnet.
- [ ] **Client 2:** Tailscale connected on group Tailnet.
- [ ] **Client 3:** Tailscale connected on group Tailnet.
- [ ] **Server Firewall:** PowerShell inbound rule executed on Server laptop for port 8080.

### Phase 2: Server Startup
- [ ] Run `Start-Server.bat` on Server laptop.
- [ ] Confirm console prints `Server address: http://100.A.B.C:8080`.
- [ ] Confirm console prints `Database: ...\Server\data\chat.db`.
- [ ] Confirm no exceptions or port binding errors.

### Phase 3: Client Launch & Login
- [ ] **Client 1:** Set `SERVER_URL=http://100.A.B.C:8080` in `client-config.ini`. Run `Start-Client.bat`.
  - [ ] Browser opens automatically to login screen.
  - [ ] Log in as `alice` / `chat1234`. Status becomes `CONNECTED`.
- [ ] **Client 2:** Set `client-config.ini`. Run `Start-Client.bat`.
  - [ ] Log in as `bob` / `chat1234`. Status becomes `CONNECTED`.
- [ ] **Client 3:** Set `client-config.ini`. Run `Start-Client.bat`.
  - [ ] Log in as `carol` / `chat1234`. Status becomes `CONNECTED`.
- [ ] **Server Laptop:** Open browser to `http://localhost:8080`.
  - [ ] Log in as `dave` / `chat1234`.

### Phase 4: Presence & Real-Time Messaging Verification
- [ ] In all 4 browsers, verify that all 4 users show green `Online` badges in the sidebar.
- [ ] User `alice` types "Hello team from Alice!" and presses Enter.
- [ ] Confirm the message appears in Alice's browser aligned to the right in blue.
- [ ] Confirm the message appears in Bob, Carol, and Dave's browsers instantly on the left with sender name and timestamp.
- [ ] User `bob` sends a reply. Confirm all 4 screens update simultaneously.

### Phase 5: Reconnection & History Synchronization
- [ ] On Client 1 (Alice), disconnect Wi-Fi or disconnect Tailscale.
  - [ ] Confirm Client 1 status badge changes to `DISCONNECTED`.
  - [ ] Confirm Bob, Carol, and Dave see Alice's status update to `Offline` in real time.
- [ ] While Alice is disconnected, Bob sends "Message sent while Alice is offline".
- [ ] Reconnect Client 1 (Alice) to the network.
  - [ ] Confirm Alice's status transitions from `Connecting…` to `CONNECTED`.
  - [ ] Confirm the message sent while offline loads automatically into Alice's message stream.
  - [ ] Confirm Bob, Carol, and Dave see Alice return to `Online`.

### Phase 6: Server Restart & Database Persistence
- [ ] On the Server terminal, press `CTRL+C`.
  - [ ] Confirm all client browsers transition to `DISCONNECTED`.
- [ ] Restart `Start-Server.bat` on the Server laptop.
  - [ ] Confirm all client browsers automatically reconnect without manual page refresh.
  - [ ] Refresh any client browser (F5) — confirm message history remains fully intact.
  - [ ] Verify `Server/data/chat.db` retains all messages.

