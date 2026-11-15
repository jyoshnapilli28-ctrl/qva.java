-- =============================================
-- V3__rename_accounts_to_custom_users.sql
-- Safely renames predefined seed accounts to the 4 custom deployment accounts:
--   alice  -> jyoshna (Host laptop user)
--   bob    -> meghana (Client 1)
--   carol  -> yash    (Client 2)
--   dave   -> yashuu  (Client 3)
--
-- Preserves user primary keys (id 1..4), foreign key relationships,
-- and all existing message history.
-- =============================================

-- Safely rename existing records if present (case-insensitive match)
UPDATE users SET username = 'jyoshna', display_name = 'Jyoshna' WHERE LOWER(username) = 'alice';
UPDATE users SET username = 'meghana', display_name = 'Meghana' WHERE LOWER(username) = 'bob';
UPDATE users SET username = 'yash',    display_name = 'Yash'    WHERE LOWER(username) = 'carol';
UPDATE users SET username = 'yashuu',  display_name = 'Yashuu'  WHERE LOWER(username) = 'dave';

-- For fresh installations where users table might be seeded differently, ensure the 4 accounts exist
INSERT OR IGNORE INTO users (id, username, password_hash, display_name) VALUES
    (1, 'jyoshna', '$2a$12$SH9Nx9MTSmzHtFNjoPw6mOQvMAh9iVxuZw5x3v0kcaQFEzpFlyWDG', 'Jyoshna'),
    (2, 'meghana', '$2a$12$SH9Nx9MTSmzHtFNjoPw6mOQvMAh9iVxuZw5x3v0kcaQFEzpFlyWDG', 'Meghana'),
    (3, 'yash',    '$2a$12$SH9Nx9MTSmzHtFNjoPw6mOQvMAh9iVxuZw5x3v0kcaQFEzpFlyWDG', 'Yash'),
    (4, 'yashuu',  '$2a$12$SH9Nx9MTSmzHtFNjoPw6mOQvMAh9iVxuZw5x3v0kcaQFEzpFlyWDG', 'Yashuu');

