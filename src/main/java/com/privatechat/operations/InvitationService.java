package com.privatechat.operations;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.privatechat.operations.OperationsModels.*;

@Service
class InvitationService {
    static final int DEFAULT_EXPIRATION_HOURS = 168;
    static final int MAX_EXPIRATION_HOURS = 720;
    static final int MAX_ACTIVE_INVITATIONS = 20;
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Pattern USERNAME = Pattern.compile("[a-z0-9._-]{3,32}");
    private final SecureRandom random = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;

    InvitationService(JdbcTemplate jdbc, PasswordEncoder passwords) {
        this.jdbc = jdbc;
        this.passwords = passwords;
    }

    record CreatedInvitation(String id, String token, Instant createdAt, Instant expiresAt) {}
    record InvitationView(String id, String status, Instant createdAt, Instant expiresAt,
                          Instant revokedAt, Instant redeemedAt, String redeemedUsername) {}
    record InvitationAudit(String id, String invitationId, String action, String actorUsername,
                           String affectedUsername, Instant occurredAt) {}
    record RegisteredAccount(long userId, String username, String displayName) {}

    @Transactional
    CreatedInvitation create(long hostUserId, int expirationHours) {
        if (expirationHours < 1 || expirationHours > MAX_EXPIRATION_HOURS) {
            throw new OperationsException(HttpStatus.BAD_REQUEST, "Expiration must be between 1 and 720 hours");
        }
        byte[] secret = new byte[32];
        random.nextBytes(secret);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        java.util.Arrays.fill(secret, (byte) 0);
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        long expires = now + expirationHours * 3_600_000L;
        int inserted = jdbc.update("""
                INSERT INTO qva_invitations(id, token_hash, created_by_user_id, created_ms, expires_ms)
                SELECT ?, ?, ?, ?, ?
                WHERE (SELECT COUNT(*) FROM qva_invitations
                       WHERE revoked_ms IS NULL AND redeemed_ms IS NULL AND expires_ms > ?) < ?
                """, id, digest(token), hostUserId, now, expires, now, MAX_ACTIVE_INVITATIONS);
        if (inserted != 1) {
            throw new OperationsException(HttpStatus.CONFLICT, "The maximum number of pending invitations has been reached");
        }
        audit(id, "CREATED", hostUserId, null, now);
        return new CreatedInvitation(id, token, Instant.ofEpochMilli(now), Instant.ofEpochMilli(expires));
    }

    @Transactional(readOnly = true)
    List<InvitationView> list() {
        long now = System.currentTimeMillis();
        return jdbc.query("""
                SELECT i.id,
                    CASE WHEN i.redeemed_ms IS NOT NULL THEN 'REDEEMED'
                         WHEN i.revoked_ms IS NOT NULL THEN 'REVOKED'
                         WHEN i.expires_ms <= ? THEN 'EXPIRED' ELSE 'PENDING' END AS status,
                    i.created_ms, i.expires_ms, i.revoked_ms, i.redeemed_ms, u.username
                FROM qva_invitations i LEFT JOIN users u ON u.id = i.redeemed_user_id
                ORDER BY i.created_ms DESC LIMIT 100
                """, (row, index) -> new InvitationView(row.getString("id"), row.getString("status"),
                instant(row.getLong("created_ms")), instant(row.getLong("expires_ms")),
                nullableInstant(row.getObject("revoked_ms")), nullableInstant(row.getObject("redeemed_ms")),
                row.getString("username")), now);
    }

    @Transactional(readOnly = true)
    List<InvitationAudit> audit() {
        return jdbc.query("""
                SELECT a.id, a.invitation_id, a.action, actor.username AS actor_username,
                       affected.username AS affected_username, a.occurred_ms
                FROM qva_invitation_audit a
                LEFT JOIN users actor ON actor.id = a.actor_user_id
                LEFT JOIN users affected ON affected.id = a.affected_user_id
                ORDER BY a.occurred_ms DESC LIMIT 100
                """, (row, index) -> new InvitationAudit(row.getString("id"), row.getString("invitation_id"),
                row.getString("action"), row.getString("actor_username"), row.getString("affected_username"),
                instant(row.getLong("occurred_ms"))));
    }

    @Transactional
    void revoke(long hostUserId, String invitationId) {
        long now = System.currentTimeMillis();
        int changed = jdbc.update("""
                UPDATE qva_invitations SET revoked_ms = ?
                WHERE id = ? AND revoked_ms IS NULL AND redeemed_ms IS NULL AND expires_ms > ?
                """, now, invitationId, now);
        if (changed != 1) throw new OperationsException(HttpStatus.CONFLICT, "Invitation is unavailable or already used");
        audit(invitationId, "REVOKED", hostUserId, null, now);
    }

    @Transactional
    RegisteredAccount register(String suppliedToken, String suppliedUsername,
                                             String suppliedDisplayName, String suppliedPassword) {
        if (suppliedToken == null || !TOKEN.matcher(suppliedToken).matches()) {
            throw new OperationsException(HttpStatus.BAD_REQUEST, "A valid invitation is required");
        }
        String username = suppliedUsername == null ? "" : suppliedUsername.strip().toLowerCase(Locale.ROOT);
        String displayName = suppliedDisplayName == null ? "" : suppliedDisplayName.strip();
        if (!USERNAME.matcher(username).matches()) {
            throw new OperationsException(HttpStatus.BAD_REQUEST, "Username must be 3–32 letters, digits, dots, underscores, or hyphens");
        }
        if (displayName.isEmpty() || displayName.length() > 60
                || displayName.chars().anyMatch(Character::isISOControl)) {
            throw new OperationsException(HttpStatus.BAD_REQUEST, "Display name must contain 1–60 visible characters");
        }
        if (suppliedPassword == null || suppliedPassword.codePointCount(0, suppliedPassword.length()) < 12
                || suppliedPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new OperationsException(HttpStatus.BAD_REQUEST, "Password must be at least 12 characters and no more than 72 UTF-8 bytes");
        }
        String passwordHash = passwords.encode(suppliedPassword);
        String tokenHash = digest(suppliedToken);
        long now = System.currentTimeMillis();
        // Make the transaction's first statement a write so concurrent requests do not upgrade a read snapshot.
        int inserted = jdbc.update("""
                INSERT INTO users(username, password_hash, display_name)
                SELECT ?, ?, ?
                WHERE EXISTS (SELECT 1 FROM qva_invitations WHERE token_hash = ?
                              AND revoked_ms IS NULL AND redeemed_ms IS NULL AND expires_ms > ?)
                  AND NOT EXISTS (SELECT 1 FROM users WHERE username = ? COLLATE NOCASE)
                """, username, passwordHash, displayName, tokenHash, now, username);
        if (inserted != 1) {
            throw new OperationsException(HttpStatus.CONFLICT, "Invitation or username is unavailable");
        }
        long userId = jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
        String invitationId = jdbc.query("""
                UPDATE qva_invitations SET redeemed_ms = ?, redeemed_user_id = ?
                WHERE token_hash = ? AND revoked_ms IS NULL AND redeemed_ms IS NULL AND expires_ms > ?
                RETURNING id
                """, (row, index) -> row.getString("id"), now, userId, tokenHash, now)
                .stream().findFirst().orElseThrow(() ->
                        new OperationsException(HttpStatus.CONFLICT, "Invitation is invalid, expired, revoked, or already used"));
        audit(invitationId, "REDEEMED", userId, userId, now);
        return new RegisteredAccount(userId, username, displayName);
    }

    private void audit(String invitationId, String action, Long actor, Long affected, long now) {
        jdbc.update("""
                INSERT INTO qva_invitation_audit(id, invitation_id, action, actor_user_id, affected_user_id, occurred_ms)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID().toString(), invitationId, action, actor, affected, now);
    }

    private static Instant instant(long value) { return Instant.ofEpochMilli(value); }
    private static Instant nullableInstant(Object value) {
        return value instanceof Number number ? Instant.ofEpochMilli(number.longValue()) : null;
    }

    private static String digest(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
