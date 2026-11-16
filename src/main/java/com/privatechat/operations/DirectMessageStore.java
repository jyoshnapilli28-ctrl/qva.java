package com.privatechat.operations;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.http.HttpStatus.*;

@Repository
class DirectMessageStore {
    record Conversation(String id, long peerId, String peerName, Instant createdAt) {}
    record Envelope(long sequence, long senderUserId, String senderDeviceId,
                    int version, byte[] envelope, Instant acceptedAt) {}
    private record Stored(String conversationId, Envelope envelope) {}

    private static final String CONVERSATIONS = """
            SELECT c.id, u.id AS peer_id, u.display_name AS peer_name, c.created_ms
            FROM qva_direct_conversations c JOIN users u
                ON u.id = CASE WHEN c.lower_user_id = ? THEN c.upper_user_id ELSE c.lower_user_id END
            WHERE (c.lower_user_id = ? OR c.upper_user_id = ?)
            """;
    private static final RowMapper<Conversation> CONVERSATION = (rs, row) ->
            new Conversation(rs.getString("id"), rs.getLong("peer_id"), rs.getString("peer_name"),
                    Instant.ofEpochMilli(rs.getLong("created_ms")));
    static final RowMapper<Envelope> ENVELOPE = (rs, row) ->
            new Envelope(rs.getLong("sequence"), rs.getLong("sender_user_id"),
                    rs.getString("sender_device_id"), rs.getInt("protocol_version"), rs.getBytes("envelope"),
                    Instant.ofEpochMilli(rs.getLong("accepted_ms")));
    private final JdbcTemplate jdbc;

    DirectMessageStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public Conversation open(long user, long peer) {
        if (peer <= 0 || peer == user) {
            throw new OperationsException(BAD_REQUEST, "Choose another account");
        }
        long lower = Math.min(user, peer), upper = Math.max(user, peer);
        jdbc.update("""
                INSERT INTO qva_direct_conversations(id, lower_user_id, upper_user_id, created_ms)
                SELECT ?, ?, ?, ? WHERE EXISTS (SELECT 1 FROM users WHERE id = ? AND is_active = 1)
                ON CONFLICT(lower_user_id, upper_user_id) DO NOTHING
                """, UUID.randomUUID().toString(), lower, upper, System.currentTimeMillis(), peer);
        return jdbc.query(CONVERSATIONS + " AND c.lower_user_id = ? AND c.upper_user_id = ? AND u.is_active = 1",
                CONVERSATION, user, user, user, lower, upper).stream().findFirst()
                .orElseThrow(() -> new OperationsException(NOT_FOUND, "Account unavailable"));
    }

    List<Conversation> list(long user) {
        return jdbc.query(CONVERSATIONS + " ORDER BY c.created_ms DESC, c.id LIMIT 100",
                CONVERSATION, user, user, user);
    }

    List<Envelope> history(long user, String conversation, long before, long after) {
        requireMember(user, conversation);
        return jdbc.query("""
                SELECT * FROM qva_message_envelopes
                WHERE conversation_id = ? AND sequence < ? AND sequence > ? AND accepted_ms >= ?
                ORDER BY sequence
                """ + (after > 0 ? " ASC" : " DESC") + " LIMIT 50",
                ENVELOPE, conversation, before, after, MessageLifecycle.cutoff());
    }

    @Transactional
    public Envelope submit(long user, String device, String conversation, String request, int version, byte[] envelope) {
        jdbc.update("""
                INSERT INTO qva_message_envelopes
                (conversation_id, sender_user_id, sender_device_id, request_id, protocol_version, envelope, accepted_ms)
                SELECT c.id, ?, d.id, ?, ?, ?, ?
                FROM qva_direct_conversations c JOIN qva_devices d ON d.id = ?
                WHERE c.id = ? AND (c.lower_user_id = ? OR c.upper_user_id = ?)
                  AND d.user_id = ? AND d.status = 'APPROVED'
                  AND NOT EXISTS(SELECT 1 FROM qva_retired_requests WHERE sender_device_id=d.id AND request_id=?)
                  AND NOT EXISTS(SELECT 1 FROM qva_group_envelopes WHERE sender_device_id=d.id AND request_id=?)
                ON CONFLICT(sender_device_id, request_id) DO NOTHING
                """, user, request, version, envelope, System.currentTimeMillis(), device, conversation, user, user, user, request, request);
        requireMember(user, conversation);
        Optional<Stored> result = jdbc.query("""
                SELECT m.* FROM qva_message_envelopes m JOIN qva_devices d ON d.id = m.sender_device_id
                WHERE m.sender_device_id = ? AND m.request_id = ? AND d.status = 'APPROVED'
                """, (rs, row) -> new Stored(rs.getString("conversation_id"), ENVELOPE.mapRow(rs, row)),
                device, request).stream().findFirst();
        Stored stored = result.orElseThrow(() -> new OperationsException(CONFLICT, "Envelope unavailable or request ID already used"));
        if (!stored.conversationId().equals(conversation) || stored.envelope().version() != version
                || !Arrays.equals(stored.envelope().envelope(), envelope)) {
            throw new OperationsException(CONFLICT, "Request ID already used");
        }
        if (stored.envelope().acceptedAt().toEpochMilli() < MessageLifecycle.cutoff()) throw new OperationsException(GONE, "Envelope expired");
        return stored.envelope();
    }

    private void requireMember(long user, String conversation) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM qva_direct_conversations
                WHERE id = ? AND (lower_user_id = ? OR upper_user_id = ?))
                """, Boolean.class, conversation, user, user))) {
            throw new OperationsException(NOT_FOUND, "Conversation unavailable");
        }
    }
}
