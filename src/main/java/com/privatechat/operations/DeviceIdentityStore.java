package com.privatechat.operations;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.springframework.http.HttpStatus.*;

@Repository
class DeviceIdentityStore {
    private static final long PACKAGE_LIFETIME_MS = Duration.ofDays(7).toMillis();
    private static final int MAX_ACTIVE_PACKAGES = 32;

    private static final String APPROVED_ACTOR = """
            WITH actor AS (
                SELECT d.id, d.user_id
                FROM qva_devices d JOIN users u ON u.id = d.user_id
                WHERE d.id = ? AND d.user_id = ? AND d.status = 'APPROVED' AND u.is_active = 1
            )
            """;

    private static final String CONVERSATION_PEER = APPROVED_ACTOR + """
            , peer AS (
                SELECT i.*, d.user_id,
                       (SELECT own.version FROM qva_current_device_identities own
                        WHERE own.device_id = a.id) AS verifier_version
                FROM actor a
                JOIN qva_identity_conversation_members caller
                    ON caller.user_id = a.user_id AND caller.conversation_id = ?
                JOIN qva_identity_conversation_members member
                    ON member.conversation_id = caller.conversation_id
                JOIN qva_devices d ON d.user_id = member.user_id
                JOIN users u ON u.id = d.user_id
                JOIN qva_current_device_identities i ON i.device_id = d.id
                WHERE d.id = ? AND d.id <> a.id AND d.status = 'APPROVED' AND u.is_active = 1
            )
            """;

    private static final RowMapper<Identity> IDENTITY = (row, number) -> new Identity(
            row.getString("device_id"), row.getLong("user_id"), row.getInt("version"),
            row.getString("fingerprint"), row.getBytes("signing_key"), "CURRENT", row.getString("verification"),
            Instant.ofEpochMilli(row.getLong("registered_ms")));

    private final JdbcTemplate jdbc;

    DeviceIdentityStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record Identity(String deviceId, long userId, int version, String fingerprint, byte[] signingKey,
                    String status, String verification, Instant registeredAt) {}

    record Publication(String id, int identityVersion, Instant expiresAt) {}

    record Claim(String id, String deviceId, int identityVersion, byte[] keyPackage, Instant expiresAt) {}

    Identity own(OperationsModels.Device device) {
        return jdbc.query(APPROVED_ACTOR + """
                SELECT i.*, a.user_id, 'SELF' AS verification
                FROM actor a JOIN qva_current_device_identities i ON i.device_id = a.id
                """, IDENTITY, device.id(), device.userId()).stream().findFirst()
                .orElseThrow(() -> new OperationsException(NOT_FOUND, "Device identity unavailable"));
    }

    @Transactional
    public Identity register(OperationsModels.Device device, int expectedVersion, byte[] key) {
        int inserted = jdbc.update(APPROVED_ACTOR + """
                INSERT INTO qva_device_identities(device_id, version, signing_key, fingerprint, registered_ms)
                SELECT a.id, ?, ?, ?, ? FROM actor a
                WHERE COALESCE((SELECT version FROM qva_current_device_identities WHERE device_id = a.id), 0) = ?
                ON CONFLICT DO NOTHING
                """, device.id(), device.userId(), expectedVersion + 1, key, digest(key),
                System.currentTimeMillis(), expectedVersion);
        if (inserted != 1) {
            throw new OperationsException(CONFLICT, "Credential version or signing key is unavailable");
        }
        jdbc.update("""
                UPDATE qva_device_key_packages SET package_bytes = NULL
                WHERE device_id = ? AND identity_version < ? AND package_bytes IS NOT NULL
                """, device.id(), expectedVersion + 1);
        return own(device);
    }

    Identity peer(OperationsModels.Device device, UUID conversation, UUID peerDevice) {
        return jdbc.query(CONVERSATION_PEER + """
                SELECT p.*, CASE
                    WHEN EXISTS (SELECT 1 FROM qva_identity_verifications v
                        WHERE v.verifier_device_id = a.id AND v.verifier_version = p.verifier_version
                        AND v.peer_device_id = p.device_id AND v.peer_version = p.version) THEN 'VERIFIED'
                    WHEN EXISTS (SELECT 1 FROM qva_identity_verifications v
                        WHERE v.verifier_device_id = a.id AND v.verifier_version = p.verifier_version
                        AND v.peer_device_id = p.device_id AND v.peer_version < p.version) THEN 'KEY_CHANGED'
                    ELSE 'UNVERIFIED' END AS verification
                FROM peer p CROSS JOIN actor a
                """, IDENTITY, device.id(), device.userId(), conversation.toString(), peerDevice.toString())
                .stream().findFirst().orElseThrow(() -> new OperationsException(NOT_FOUND, "Peer identity unavailable"));
    }

    @Transactional
    public Identity verify(OperationsModels.Device device, UUID conversation, UUID peerDevice,
                           int version, String fingerprint) {
        int recorded = jdbc.update(CONVERSATION_PEER + """
                INSERT INTO qva_identity_verifications
                    (verifier_device_id, verifier_version, peer_device_id, peer_version, verified_ms)
                SELECT a.id, p.verifier_version, p.device_id, p.version, ?
                FROM peer p CROSS JOIN actor a
                WHERE p.version = ? AND p.fingerprint = ? AND p.verifier_version IS NOT NULL
                ON CONFLICT (verifier_device_id, verifier_version, peer_device_id, peer_version)
                DO UPDATE SET verified_ms = qva_identity_verifications.verified_ms
                """, device.id(), device.userId(), conversation.toString(), peerDevice.toString(),
                System.currentTimeMillis(), version, fingerprint);
        if (recorded != 1) {
            peer(device, conversation, peerDevice);
            throw new OperationsException(CONFLICT, "Current local identity and matching peer fingerprint are required");
        }
        return peer(device, conversation, peerDevice);
    }

    @Transactional
    public Publication publish(OperationsModels.Device device, int version, byte[] packet) {
        long now = System.currentTimeMillis();
        long expires = now + PACKAGE_LIFETIME_MS;
        String id = UUID.randomUUID().toString();
        int inserted = jdbc.update(APPROVED_ACTOR + """
                INSERT INTO qva_device_key_packages
                    (id, device_id, identity_version, digest, package_bytes, published_ms, expires_ms)
                SELECT ?, a.id, i.version, ?, ?, ?, ?
                FROM actor a JOIN qva_current_device_identities i ON i.device_id = a.id
                WHERE i.version = ? AND (
                    SELECT COUNT(*) FROM qva_device_key_packages k
                    WHERE k.device_id = a.id AND k.identity_version = i.version
                    AND k.expires_ms > ? AND k.claimed_ms IS NULL AND k.package_bytes IS NOT NULL
                ) < ?
                ON CONFLICT DO NOTHING
                """, device.id(), device.userId(), id, digest(packet), packet, now, expires,
                version, now, MAX_ACTIVE_PACKAGES);
        if (inserted != 1) {
            throw new OperationsException(CONFLICT, "Current credential, unique package and available pool capacity are required");
        }
        jdbc.update("""
                UPDATE qva_device_key_packages SET package_bytes = NULL
                WHERE device_id = ? AND expires_ms <= ? AND package_bytes IS NOT NULL
                """, device.id(), now);
        return new Publication(id, version, Instant.ofEpochMilli(expires));
    }

    @Transactional
    public Claim claim(OperationsModels.Device device, UUID conversation, UUID peerDevice, int version, UUID claimId) {
        long now = System.currentTimeMillis();
        // Acquire the SQLite writer lock before selecting an available package.
        var claimed = jdbc.query(CONVERSATION_PEER + """
                UPDATE qva_device_key_packages
                SET claimed_by = ?, claim_id = ?, claimed_ms = ?
                WHERE id = (
                    SELECT k.id FROM qva_device_key_packages k JOIN peer p ON p.device_id = k.device_id
                    JOIN qva_identity_verifications v
                        ON v.peer_device_id = p.device_id AND v.peer_version = p.version
                        AND v.verifier_version = p.verifier_version
                    JOIN actor a ON a.id = v.verifier_device_id
                    WHERE p.version = ? AND k.identity_version = p.version AND k.claimed_ms IS NULL
                    AND k.package_bytes IS NOT NULL AND k.expires_ms > ?
                    AND NOT EXISTS (SELECT 1 FROM qva_device_key_packages prior
                                    WHERE prior.claimed_by = a.id AND prior.claim_id = ?)
                    ORDER BY k.published_ms, k.id LIMIT 1
                ) AND claimed_ms IS NULL
                RETURNING id, device_id, identity_version, package_bytes, expires_ms
                """, (row, number) -> new Claim(row.getString("id"), row.getString("device_id"),
                        row.getInt("identity_version"), row.getBytes("package_bytes"),
                        Instant.ofEpochMilli(row.getLong("expires_ms"))),
                device.id(), device.userId(), conversation.toString(), peerDevice.toString(),
                device.id(), claimId.toString(), now, version, now, claimId.toString());
        if (claimed.isEmpty()) {
            Identity current = peer(device, conversation, peerDevice);
            if (current.version() != version || !"VERIFIED".equals(current.verification())) {
                throw new OperationsException(CONFLICT, "Current peer credential verification is required");
            }
            throw new OperationsException(CONFLICT, "Claim already used or no KeyPackage available");
        }
        Claim result = claimed.get(0);
        jdbc.update("UPDATE qva_device_key_packages SET package_bytes = NULL WHERE id = ?", result.id());
        return result;
    }

    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
