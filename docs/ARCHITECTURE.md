# Architecture

## Overview

```
                  TAILSCALE PRIVATE NETWORK (100.x.x.x/24)
                              │
           ┌──────────────────┼──────────────────┐
           │                  │                  │
      SERVER LAPTOP       CLIENT 1          CLIENT 2
      Java 17 + Spring    Windows           Windows
      SQLite              Browser           Browser
      Port 8080                                   │
           │                                 CLIENT 3
           │                                 Windows
           └─────────────────────────────────Browser
```

## Backend Components

```
PrivateChatApplication
│
├── Config Layer
│   ├── SecurityConfig      Form-login, BCrypt, session management
│   ├── WebSocketConfig     STOMP broker, /ws/chat endpoint
│   └── WebMvcConfig        CORS for Tailscale IPs
│
├── Controller Layer (REST)
│   ├── AuthController      /api/auth/me, /api/auth/status
│   ├── UserController      /api/users
│   ├── MessageController   /api/rooms/{room}/messages
│   └── HealthController    /api/health
│
├── WebSocket Layer
│   ├── ChatWebSocketController   @MessageMapping /app/chat.send
│   └── WebSocketEventListener   Session connect/disconnect events
│
├── Service Layer
│   ├── MessageService       Validate, persist, broadcast messages
│   ├── UserService          User info + online status
│   ├── UserDetailsServiceImpl  Spring Security user loading
│   └── OnlinePresenceService  Thread-safe online user tracking
│
├── Repository Layer (JPA)
│   ├── MessageRepository
│   ├── UserRepository
│   └── ChatRoomRepository
│
└── Model Layer (Entities)
    ├── Message
    ├── User
    └── ChatRoom
```

## Request Flow: Sending a Message

```
Browser (STOMP over SockJS)
    │  SEND /app/chat.send  { content, roomName }
    ▼
ChatWebSocketController.handleMessage()
    │  username from Principal
    ▼
MessageService.sendMessage()
    │  validate → save to SQLite → broadcast
    ▼
SimpMessagingTemplate.convertAndSend("/topic/messages/general", MessageDto)
    │
    ▼
All subscribers (all connected browsers)
```

## Authentication Flow

```
Browser
    │  POST /api/auth/login  (form urlencoded: username, password)
    ▼
Spring Security DaoAuthenticationProvider
    │  BCrypt verify password
    ▼
UserDetailsServiceImpl.loadUserByUsername()
    │  query SQLite users table
    ▼
Session created (JSESSIONID cookie)
    ▼
Browser receives { success: true, username: "alice" }
```

## WebSocket Connection Lifecycle

```
Browser connects SockJS → /ws/chat
    │
WebSocketEventListener.handleSessionConnected()
    │  records user as online
    │  broadcasts PresenceEvent(CONNECTED) → /topic/presence
    ▼
All clients update member list

Browser disconnects (tab close, network drop, timeout)
    │
WebSocketEventListener.handleSessionDisconnect()
    │  removes user from online set
    │  broadcasts PresenceEvent(DISCONNECTED)
    ▼
All clients mark user offline

Client reconnection:
    chat.js schedules reconnect with exponential backoff (2s → 4s → 8s … max 30s)
    After reconnect, loads full history via REST before re-subscribing
```

## Database Schema

See [DATABASE.md](DATABASE.md) for full schema details.

## Network Layer

See [NETWORKING.md](NETWORKING.md) for Tailscale setup and firewall guidance.

