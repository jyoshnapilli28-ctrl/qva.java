# Database Documentation

## Engine

**SQLite** — embedded, serverless, zero-configuration.

The database file lives at:
```
Server/data/chat.db
```

This path is configured via the `DB_PATH` environment variable in `Start-Server.bat`.
The path is **never inside the JAR** and is **never overwritten** when deploying a new JAR.

## WAL Mode

The schema migration enables **Write-Ahead Logging (WAL)**:
```sql
PRAGMA journal_mode=WAL;
```

WAL allows concurrent readers while a write is in progress, which improves
responsiveness when multiple users are active simultaneously.

## Schema

### `users`

| Column         | Type    | Notes                        |
|---------------|---------|------------------------------|
| `id`          | INTEGER | Primary key, autoincrement   |
| `username`    | TEXT    | Unique, indexed              |
| `password_hash` | TEXT  | BCrypt hash (cost=12)        |
| `display_name` | TEXT   | Shown in the UI              |
| `created_at`  | TEXT    | ISO 8601 UTC timestamp       |
| `last_login`  | TEXT    | ISO 8601 UTC, nullable       |
| `is_active`   | INTEGER | 1=active, 0=disabled         |

### `chat_rooms`

| Column        | Type    | Notes                        |
|--------------|---------|------------------------------|
| `id`         | INTEGER | Primary key, autoincrement   |
| `name`       | TEXT    | Unique room identifier       |
| `description` | TEXT   | Nullable                     |
| `created_at` | TEXT    | ISO 8601 UTC timestamp       |

One room is seeded: `general`.

### `messages`

| Column       | Type    | Notes                                |
|-------------|---------|--------------------------------------|
| `id`        | INTEGER | Primary key, autoincrement           |
| `room_id`   | INTEGER | FK → chat_rooms.id                   |
| `user_id`   | INTEGER | FK → users.id                        |
| `content`   | TEXT    | Message text (max 2000 chars)        |
| `sent_at`   | TEXT    | ISO 8601 UTC timestamp               |
| `is_deleted` | INTEGER | Soft delete flag (reserved for future) |

**Indexes:**
- `idx_messages_room_sent` on `(room_id, sent_at)` — used for history queries
- `idx_messages_user` on `(user_id)`

## Timestamps

All timestamps are stored in **UTC** (`strftime('%Y-%m-%dT%H:%M:%SZ', 'now')`).

The frontend displays them in the **user's local timezone** using `Date.toLocaleTimeString()`.

## Migrations

Database migrations are managed by **Flyway**.

Migration files live in:
```
src/main/resources/db/migration/
```

Naming convention: `V{version}__{description}.sql`

Flyway runs automatically at startup and applies any pending migrations.
The database schema is **never dropped** on restart.

## Backup

Perform a backup by copying `data/chat.db` to the `backups/` directory.

A simple approach (run while server is NOT under heavy write load):

```cmd
copy Server\data\chat.db Server\backups\chat-backup-%date:~-4,4%%date:~-7,2%%date:~0,2%.db
```

Or use SQLite's online backup mode via the CLI:
```cmd
sqlite3 data/chat.db ".backup backups/chat-backup.db"
```

## Changing User Passwords

To change a password:

1. Generate a new BCrypt hash (cost 12):
   - Use: https://bcrypt-generator.com
   - Or run: `python3 -c "import bcrypt; print(bcrypt.hashpw(b'newpassword', bcrypt.gensalt(12)).decode())"`

2. Update the database directly:
   ```sql
   UPDATE users SET password_hash='$2a$12$...' WHERE username='alice';
   ```

3. Or edit `V1__initial_schema.sql` before first run.

## Survival Guarantees

The database survives:
- Browser refresh ✓
- Client disconnection ✓  
- Client reconnection ✓
- Server CTRL+C ✓
- Windows restart ✓
- JAR upgrade (new JAR replaces old, database untouched) ✓

