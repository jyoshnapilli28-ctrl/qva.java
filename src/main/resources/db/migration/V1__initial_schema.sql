-- =============================================
-- V1__initial_schema.sql
-- Initial database schema for Private Chat
--
-- Note: PRAGMA statements that change DB settings are run
-- by a separate Flyway callback. Only DDL and DML here.
-- =============================================

-- =============================================
-- Users table
-- =============================================
CREATE TABLE IF NOT EXISTS users (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    username    TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    display_name TEXT NOT NULL,
    created_at  TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
    last_login  TEXT,
    is_active   INTEGER NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);

-- =============================================
-- Chat rooms table
-- =============================================
CREATE TABLE IF NOT EXISTS chat_rooms (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT NOT NULL UNIQUE,
    description TEXT,
    created_at  TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

-- =============================================
-- Messages table
-- =============================================
CREATE TABLE IF NOT EXISTS messages (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    room_id     INTEGER NOT NULL,
    user_id     INTEGER NOT NULL,
    content     TEXT NOT NULL,
    sent_at     TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
    is_deleted  INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (room_id) REFERENCES chat_rooms(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_messages_room_sent ON messages(room_id, sent_at);
CREATE INDEX IF NOT EXISTS idx_messages_user ON messages(user_id);

-- =============================================
-- Seed data
-- =============================================

-- Insert the default "General" chat room
INSERT OR IGNORE INTO chat_rooms (name, description)
VALUES ('general', 'The main shared chat room');

-- Insert the four authorized users
-- Passwords are BCrypt hashes. Default password for all users: "chat1234"
-- IMPORTANT: Change these passwords before deployment!
-- Generate new BCrypt hashes at: https://bcrypt-generator.com (cost=12)
INSERT OR IGNORE INTO users (username, password_hash, display_name) VALUES
    ('alice',   '$2a$12$Ib0JXgdJzjAIJsf1xhd1k.6nqODVTPXr1QETF3JaO7JNKL/m9vwGi', 'Alice'),
    ('bob',     '$2a$12$Ib0JXgdJzjAIJsf1xhd1k.6nqODVTPXr1QETF3JaO7JNKL/m9vwGi', 'Bob'),
    ('carol',   '$2a$12$Ib0JXgdJzjAIJsf1xhd1k.6nqODVTPXr1QETF3JaO7JNKL/m9vwGi', 'Carol'),
    ('dave',    '$2a$12$Ib0JXgdJzjAIJsf1xhd1k.6nqODVTPXr1QETF3JaO7JNKL/m9vwGi', 'Dave');
