package com.privatechat.operations;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.privatechat.operations.OperationsModels.*;

@Repository
class DeviceStore {
    private static final String SELECT = """
        SELECT d.id, d.user_id, u.username, d.name, d.status,
               d.registered_ms, d.last_seen_ms, d.approved_ms, d.revoked_ms
        FROM qva_devices d JOIN users u ON u.id = d.user_id
        """;
    private static final RowMapper<Device> DEVICE = (row, number) -> new Device(
            row.getString("id"), row.getLong("user_id"), row.getString("username"),
            row.getString("name"), DeviceStatus.valueOf(row.getString("status")),
            instant(row, "registered_ms"), instant(row, "last_seen_ms"),
            instant(row, "approved_ms"), instant(row, "revoked_ms"));
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DeviceStore.class);
    private final java.util.concurrent.atomic.AtomicBoolean conflictReported = new java.util.concurrent.atomic.AtomicBoolean();
    private final JdbcTemplate jdbc;

    DeviceStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private record HostGrant(long userId, String id) {}

    String grantId(long userId) {
        var grants = jdbc.query("SELECT user_id, grant_id FROM qva_host_grants LIMIT 2",
                (row, number) -> new HostGrant(row.getLong("user_id"), row.getString("grant_id")));
        if (grants.size() > 1 && conflictReported.compareAndSet(false, true)) {
            log.error("Conflicting QVA HOST grants detected. Administrative access is disabled.");
        }
        return grants.size() == 1 && grants.get(0).userId() == userId ? grants.get(0).id() : null;
    }

    boolean isHost(long userId) { return grantId(userId) != null; }

    Optional<Device> byCredential(long userId, String digest) {
        return jdbc.query(SELECT + " WHERE d.user_id = ? AND d.credential_digest = ?",
                DEVICE, userId, digest).stream().findFirst();
    }

    Optional<Device> byId(String id) {
        return jdbc.query(SELECT + " WHERE d.id = ?", DEVICE, id).stream().findFirst();
    }

    List<Device> all() {
        return jdbc.query(SELECT + " ORDER BY d.registered_ms DESC, d.id", DEVICE);
    }

    boolean register(String id, long userId, String name, String digest, long now) {
        return jdbc.update("""
                INSERT INTO qva_devices(id, user_id, name, credential_digest, status, registered_ms)
                SELECT ?, ?, ?, ?, 'PENDING', ?
                WHERE (SELECT COUNT(*) FROM qva_devices WHERE user_id = ? AND status != 'REVOKED') < 8
                """, id, userId, name, digest, now, userId) == 1;
    }

    boolean hasApprovedBefore(long userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM qva_devices WHERE user_id = ? AND approved_ms IS NOT NULL)",
                Boolean.class, userId));
    }

    boolean approveInitialHost(String id, long userId) {
        return jdbc.update("""
                UPDATE qva_devices SET status = 'APPROVED', approved_ms = ?
                WHERE id = ? AND user_id = ? AND status = 'PENDING'
                AND (SELECT COUNT(*) FROM qva_host_grants) = 1
                AND EXISTS (SELECT 1 FROM qva_host_grants WHERE user_id = ?)
                AND NOT EXISTS (SELECT 1 FROM qva_devices WHERE user_id = ? AND approved_ms IS NOT NULL)
                """, System.currentTimeMillis(), id, userId, userId, userId) == 1;
    }

    boolean approve(String id) {
        return jdbc.update("""
                UPDATE qva_devices SET status = 'APPROVED', approved_ms = ?
                WHERE id = ? AND status = 'PENDING'
                """, System.currentTimeMillis(), id) == 1;
    }

    boolean revoke(String id) {
        return jdbc.update("""
                UPDATE qva_devices SET status = 'REVOKED', revoked_ms = ?
                WHERE id = ? AND status != 'REVOKED'
                """, System.currentTimeMillis(), id) == 1;
    }

    void seen(String id, long now) {
        jdbc.update("""
                UPDATE qva_devices SET last_seen_ms = ? WHERE id = ? AND status = 'APPROVED'
                AND (last_seen_ms IS NULL OR last_seen_ms <= ?)
                """, now, id, now - 5_000);
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        long epoch = row.getLong(column);
        return row.wasNull() ? null : Instant.ofEpochMilli(epoch);
    }
}
