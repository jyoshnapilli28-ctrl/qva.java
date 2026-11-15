-- =============================================
-- V2__fix_user_password_hashes.sql
-- Update default user passwords to valid BCrypt hashes for "chat1234" (cost 12)
-- =============================================

UPDATE users
SET password_hash = '$2a$12$SH9Nx9MTSmzHtFNjoPw6mOQvMAh9iVxuZw5x3v0kcaQFEzpFlyWDG'
WHERE username IN ('alice', 'bob', 'carol', 'dave');

