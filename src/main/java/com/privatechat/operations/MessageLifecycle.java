package com.privatechat.operations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.*;

@Repository
class MessageLifecycle {
    record Group(String id, String name, long ownerId, Instant createdAt) {}
    record Participant(long id, String name) {}
    record Receipt(long sequence, long recipientUserId, Instant deliveredAt, Instant readAt) {}
    record Access(boolean group, long after) {}
    private static final long RETENTION_MS = Duration.ofDays(30).toMillis();
    private static final long RETRY_GRACE_MS = Duration.ofDays(7).toMillis();
    private final JdbcTemplate jdbc;
    private final boolean enabled;

    MessageLifecycle(JdbcTemplate jdbc, @Value("${qva.messaging.enabled:false}") boolean enabled) {
        this.jdbc = jdbc;
        this.enabled = enabled;
    }

    Access membership(long user, String id) {
        var direct = jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM qva_direct_conversations
                WHERE id = ? AND (lower_user_id = ? OR upper_user_id = ?))
                """, Boolean.class, id, user, user);
        if (Boolean.TRUE.equals(direct)) return new Access(false, 0);
        return jdbc.query("SELECT after_sequence FROM qva_group_members WHERE group_id = ? AND user_id = ?",
                (rs, row) -> new Access(true, rs.getLong(1)), id, user)
                .stream().findFirst().orElseThrow(() -> new OperationsException(NOT_FOUND, "Conversation unavailable"));
    }

    List<Group> groups(long user) {
        return jdbc.query("""
                SELECT g.* FROM qva_groups g JOIN qva_group_members m ON m.group_id = g.id
                WHERE m.user_id = ? ORDER BY g.created_ms DESC, g.id LIMIT 100
                """, (rs, row) -> new Group(rs.getString("id"), rs.getString("name"), rs.getLong("owner_user_id"),
                Instant.ofEpochMilli(rs.getLong("created_ms"))), user);
    }

    @Transactional
    public Group create(long user, String name) {
        String title = name == null ? "" : name.strip();
        if (title.isEmpty() || title.length() > 60 || title.chars().anyMatch(Character::isISOControl)) {
            throw new OperationsException(BAD_REQUEST, "Choose a group name up to 60 characters");
        }
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        jdbc.update("INSERT INTO qva_groups VALUES (?,?,?,?)", id, title, user, now);
        jdbc.update("INSERT INTO qva_group_members VALUES (?,?,0)", id, user);
        return new Group(id, title, user, Instant.ofEpochMilli(now));
    }

    List<Participant> participants(long user, String id) {
        if (!membership(user, id).group()) throw new OperationsException(NOT_FOUND, "Group unavailable");
        return jdbc.query("""
                SELECT u.id,u.display_name FROM users u JOIN qva_group_members m ON m.user_id = u.id
                WHERE m.group_id = ? AND EXISTS(SELECT 1 FROM qva_group_members WHERE group_id = ? AND user_id = ?)
                ORDER BY u.display_name,u.id
                """, (rs, row) -> new Participant(rs.getLong(1), rs.getString(2)), id, id, user);
    }

    @Transactional
    public void add(long owner, String id, long participant) {
        int changed = jdbc.update("""
                INSERT INTO qva_group_members(group_id,user_id,after_sequence)
                SELECT g.id, u.id, COALESCE((SELECT MAX(sequence) FROM qva_group_envelopes WHERE conversation_id=g.id),0)
                FROM qva_groups g JOIN users u ON u.id=? AND u.is_active=1
                WHERE g.id=? AND g.owner_user_id=?
                  AND (SELECT COUNT(*) FROM qva_group_members WHERE group_id=g.id) < 64
                ON CONFLICT(group_id,user_id) DO NOTHING
                """, participant, id, owner);
        requireOwner(owner, id);
        if (changed == 0 && !Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM qva_group_members WHERE group_id=? AND user_id=?)",
                Boolean.class, id, participant))) {
            throw new OperationsException(BAD_REQUEST, "Participant unavailable or group full");
        }
    }

    @Transactional
    public void remove(long owner, String id, long participant) {
        if (participant == owner) throw new OperationsException(BAD_REQUEST, "The group owner must remain a member");
        jdbc.update("""
                DELETE FROM qva_group_members WHERE group_id=? AND user_id=?
                   AND EXISTS(SELECT 1 FROM qva_groups WHERE id=? AND owner_user_id=?)
                """, id, participant, id, owner);
        requireOwner(owner, id);
    }

    private void requireOwner(long owner, String id) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM qva_groups WHERE id = ? AND owner_user_id = ?)
                """, Boolean.class, id, owner))) {
            throw new OperationsException(NOT_FOUND, "Group unavailable");
        }
    }

    List<DirectMessageStore.Envelope> history(long user, String id, Access access, long before, long after) {
        return jdbc.query("""
                SELECT * FROM qva_group_envelopes
                WHERE conversation_id=? AND sequence<? AND sequence>? AND accepted_ms>=?
                  AND EXISTS(SELECT 1 FROM qva_group_members m WHERE m.group_id=? AND m.user_id=?
                             AND sequence>m.after_sequence)
                ORDER BY sequence
                """ + (after > 0 ? " ASC" : " DESC") + " LIMIT 50",
                DirectMessageStore.ENVELOPE, id, before, Math.max(after, access.after()), cutoff(), id, user);
    }

    @Transactional
    public DirectMessageStore.Envelope submit(long user, String device, String id, String key, int version, byte[] bytes) {
        jdbc.update("""
                INSERT INTO qva_group_envelopes(conversation_id,sender_user_id,sender_device_id,request_id,protocol_version,envelope,accepted_ms)
                SELECT m.group_id,m.user_id,d.id,?,?,?,? FROM qva_group_members m JOIN qva_devices d
                ON d.id=? AND d.user_id=m.user_id AND d.status='APPROVED'
                WHERE m.group_id=? AND m.user_id=?
                  AND NOT EXISTS(SELECT 1 FROM qva_retired_requests WHERE sender_device_id=d.id AND request_id=?)
                  AND NOT EXISTS(SELECT 1 FROM qva_message_envelopes WHERE sender_device_id=d.id AND request_id=?)
                ON CONFLICT(sender_device_id,request_id) DO NOTHING
                """, key, version, bytes, System.currentTimeMillis(), device, id, user, key, key);
        Access access = membership(user, id);
        var entry = jdbc.query("""
                SELECT e.* FROM qva_group_envelopes e JOIN qva_devices d ON d.id=e.sender_device_id
                WHERE e.sender_device_id=? AND e.request_id=? AND d.status='APPROVED'
                """, (rs,row) -> new Stored(rs.getString("conversation_id"), DirectMessageStore.ENVELOPE.mapRow(rs,row)),
                device, key).stream().findFirst()
                .orElseThrow(() -> new OperationsException(CONFLICT, "Envelope unavailable or request ID already used"));
        if (!entry.conversation().equals(id) || entry.envelope().version()!=version
                || !Arrays.equals(entry.envelope().envelope(), bytes)) {
            throw new OperationsException(CONFLICT, "Request ID already used");
        }
        if (entry.envelope().sequence() <= access.after() || entry.envelope().acceptedAt().toEpochMilli()<cutoff()) {
            throw new OperationsException(GONE, "Envelope expired");
        }
        return entry.envelope();
    }

    private record Stored(String conversation, DirectMessageStore.Envelope envelope) {}

    @Transactional
    public Receipt acknowledge(long user, String device, String id, long sequence, boolean read) {
        long now = System.currentTimeMillis();
        int changed = jdbc.update("""
                INSERT INTO qva_message_receipts(conversation_id,sequence,recipient_user_id,delivered_ms,read_ms)
                SELECT h.conversation_id,h.sequence,?,?,? FROM qva_transport_history h
                WHERE h.conversation_id=? AND h.sequence=? AND h.sender_user_id<>? AND h.accepted_ms>=?
                AND EXISTS(SELECT 1 FROM qva_devices WHERE id=? AND user_id=? AND status='APPROVED')
                AND (EXISTS(SELECT 1 FROM qva_direct_conversations c WHERE c.id=h.conversation_id
                            AND (c.lower_user_id=? OR c.upper_user_id=?))
                     OR EXISTS(SELECT 1 FROM qva_group_members m WHERE m.group_id=h.conversation_id
                               AND m.user_id=? AND h.sequence>m.after_sequence))
                ON CONFLICT(conversation_id,sequence,recipient_user_id) DO UPDATE
                SET read_ms=COALESCE(qva_message_receipts.read_ms,excluded.read_ms)
                """, user, now, read ? now : null, id, sequence, user, cutoff(), device, user, user, user, user);
        if (changed == 0) throw new OperationsException(NOT_FOUND, "Envelope unavailable");
        return jdbc.queryForObject("""
                SELECT * FROM qva_message_receipts WHERE conversation_id=? AND sequence=? AND recipient_user_id=?
                """, RECEIPT, id, sequence, user);
    }

    private static final org.springframework.jdbc.core.RowMapper<Receipt> RECEIPT = (rs,row) -> {
        long read = rs.getLong("read_ms");
        Instant readAt = rs.wasNull() ? null : Instant.ofEpochMilli(read);
        return new Receipt(rs.getLong("sequence"), rs.getLong("recipient_user_id"),
                Instant.ofEpochMilli(rs.getLong("delivered_ms")), readAt);
    };

    List<Receipt> receipts(long user, String id, long sequence) {
        var access = membership(user, id);
        return jdbc.query("""
                SELECT r.* FROM qva_message_receipts r JOIN qva_transport_history h
                    ON r.conversation_id=h.conversation_id AND r.sequence=h.sequence
                WHERE r.conversation_id=? AND r.sequence=? AND h.sequence>? AND h.accepted_ms>=?
                  AND (h.sender_user_id=? OR r.recipient_user_id=?)
                ORDER BY r.recipient_user_id
                """, RECEIPT, id, sequence, access.after(), cutoff(), user, user);
    }

    static long cutoff() { return System.currentTimeMillis() - RETENTION_MS; }

    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void cleanup() {
        if (!enabled) return;
        long now = System.currentTimeMillis(), cutoff = now - RETENTION_MS;
        jdbc.update("""
                INSERT INTO qva_retired_requests(sender_device_id,request_id,expires_ms)
                SELECT sender_device_id,request_id,? FROM qva_transport_history WHERE accepted_ms<?
                ON CONFLICT(sender_device_id,request_id) DO NOTHING
                """, now + RETRY_GRACE_MS, cutoff);
        jdbc.update("""
                DELETE FROM qva_message_receipts WHERE EXISTS(SELECT 1 FROM qva_transport_history h
                    WHERE h.conversation_id=qva_message_receipts.conversation_id
                      AND h.sequence=qva_message_receipts.sequence AND h.accepted_ms<?)
                """, cutoff);
        jdbc.update("DELETE FROM qva_message_envelopes WHERE accepted_ms<?", cutoff);
        jdbc.update("DELETE FROM qva_group_envelopes WHERE accepted_ms<?", cutoff);
        jdbc.update("DELETE FROM qva_retired_requests WHERE expires_ms<?", now);
    }
}
