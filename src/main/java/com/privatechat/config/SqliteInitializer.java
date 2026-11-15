package com.privatechat.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Applies SQLite-specific PRAGMA settings after the application starts.
 *
 * <p>These PRAGMAs cannot be included in Flyway migrations because Flyway
 * runs migrations inside a transaction and PRAGMA journal_mode is non-transactional.
 *
 * <p>WAL mode improves concurrent read performance. It is applied once on
 * startup; SQLite persists the setting in the database file.
 */
@Component
public class SqliteInitializer {

    private static final Logger log = LoggerFactory.getLogger(SqliteInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public SqliteInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void applyPragmas() {
        try {
            String currentMode = jdbcTemplate.queryForObject("PRAGMA journal_mode", String.class);
            if (!"wal".equalsIgnoreCase(currentMode)) {
                String walMode = jdbcTemplate.queryForObject("PRAGMA journal_mode=WAL", String.class);
                log.info("SQLite journal_mode set to: {}", walMode);
            } else {
                log.debug("SQLite journal_mode is already WAL");
            }

            jdbcTemplate.execute("PRAGMA foreign_keys=ON");
            log.debug("SQLite foreign_keys enabled");

            jdbcTemplate.execute("PRAGMA synchronous=NORMAL");
            log.debug("SQLite synchronous=NORMAL");

            jdbcTemplate.execute("PRAGMA busy_timeout=5000");
            log.debug("SQLite busy_timeout=5000 configured");

            checkUninitializedPasswords();
        } catch (Exception e) {
            log.warn("Failed to apply SQLite PRAGMAs: {}", e.getMessage());
        }
    }

    private void checkUninitializedPasswords() {
        try {
            java.util.List<String> uninitialized = jdbcTemplate.query(
                    "SELECT username FROM users WHERE password_hash = '$2a$12$SH9Nx9MTSmzHtFNjoPw6mOQvMAh9iVxuZw5x3v0kcaQFEzpFlyWDG'",
                    (rs, rowNum) -> rs.getString("username")
            );
            if (!uninitialized.isEmpty()) {
                log.warn("================================================================================");
                log.warn(" [SECURITY ALERT] Uninitialized Account Passwords Detected!");
                log.warn(" The following accounts still have placeholder/uninitialized passwords:");
                for (String u : uninitialized) {
                    log.warn("   * {} (Logins are BLOCKED until initialized)", u);
                }
                log.warn(" ACTION REQUIRED: Run 'Set-Password.bat <username>' on the host server to assign");
                log.warn(" unique individual passwords before distributing client access.");
                log.warn("================================================================================");
            } else {
                log.info("All user account credentials have been individually initialized.");
            }
        } catch (Exception e) {
            log.debug("User password check skipped: {}", e.getMessage());
        }
    }
}

