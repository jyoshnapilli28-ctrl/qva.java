package com.privatechat;

import com.privatechat.dto.MessageDto;
import com.privatechat.service.MessageService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Backup and restore verification using disposable test databases.
 *
 * Procedure:
 * 1. Boot on disposable DB #1.
 * 2. Record messages with verified timestamps.
 * 3. Stop application context cleanly.
 * 4. Create backup copy of SQLite database.
 * 5. Verify backup file exists and has non-zero size.
 * 6. Restore backup copy to a new location (DB #2).
 * 7. Boot on restored DB #2.
 * 8. Verify all original messages, timestamps, and sender identities match.
 * 9. Verify restored DB can accept new messages.
 */
class BackupRestoreTest {

    private static final String ORIGINAL_DB = "./target/backup-test-original.db";
    private static final String ARCHIVE_DB = "./target/backup-test-archive.db";
    private static final String RESTORED_DB = "./target/backup-test-restored.db";

    @Test
    void testBackupAndRestoreIntegrity() throws IOException {
        cleanFiles(ORIGINAL_DB, ARCHIVE_DB, RESTORED_DB);

        String msg1 = "BackupTest-Alpha-" + System.nanoTime();
        String msg2 = "BackupTest-Beta-" + System.nanoTime();
        Instant beforeInsert = Instant.now().minusSeconds(1);

        // Step 1: Boot original database and write messages
        ConfigurableApplicationContext ctx1 = startContext(ORIGINAL_DB);
        try {
            MessageService service1 = ctx1.getBean(MessageService.class);
            service1.sendMessage("jyoshna", "general", msg1);
            service1.sendMessage("meghana", "general", msg2);
        } finally {
            ctx1.close();
        }

        File origFile = new File(ORIGINAL_DB);
        assertThat(origFile.exists()).isTrue();
        assertThat(origFile.length()).isGreaterThan(0);

        // Step 2: Create backup copy
        File archiveFile = new File(ARCHIVE_DB);
        Files.copy(origFile.toPath(), archiveFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        assertThat(archiveFile.exists()).isTrue();
        assertThat(archiveFile.length()).isEqualTo(origFile.length());

        // Step 3: Restore backup into a completely separate location
        File restoredFile = new File(RESTORED_DB);
        Files.copy(archiveFile.toPath(), restoredFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        assertThat(restoredFile.exists()).isTrue();

        // Step 4: Boot on restored database
        ConfigurableApplicationContext ctx2 = startContext(RESTORED_DB);
        try {
            MessageService service2 = ctx2.getBean(MessageService.class);
            List<MessageDto> history = service2.getHistory("general");

            // Verify original messages exist in restored DB
            MessageDto restoredMsg1 = history.stream()
                    .filter(m -> m.getContent().equals(msg1))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Message 1 not found in restored DB"));
            assertThat(restoredMsg1.getUsername()).isEqualTo("jyoshna");
            assertThat(restoredMsg1.getSentAt()).isAfterOrEqualTo(beforeInsert);

            MessageDto restoredMsg2 = history.stream()
                    .filter(m -> m.getContent().equals(msg2))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Message 2 not found in restored DB"));
            assertThat(restoredMsg2.getUsername()).isEqualTo("meghana");
            assertThat(restoredMsg2.getSentAt()).isAfterOrEqualTo(beforeInsert);

            // Step 5: Verify restored DB can accept new messages
            String postRestoreMsg = "RestoredDB-ActiveWrite-" + System.nanoTime();
            service2.sendMessage("yash", "general", postRestoreMsg);

            List<MessageDto> updatedHistory = service2.getHistory("general");
            assertThat(updatedHistory).anyMatch(m -> m.getContent().equals(postRestoreMsg));
        } finally {
            ctx2.close();
            cleanFiles(ORIGINAL_DB, ARCHIVE_DB, RESTORED_DB);
        }
    }

    private ConfigurableApplicationContext startContext(String dbPath) {
        new File("target").mkdirs();
        return new SpringApplicationBuilder(PrivateChatApplication.class)
                .properties(
                        "server.port=0",
                        "DB_PATH=" + dbPath,
                        "spring.datasource.url=jdbc:sqlite:" + dbPath,
                        "spring.flyway.enabled=true",
                        "spring.flyway.clean-disabled=false",
                        "spring.jpa.hibernate.ddl-auto=none",
                        "logging.level.com.privatechat=WARN",
                        "logging.level.org.springframework=WARN"
                )
                .run("--spring.datasource.url=jdbc:sqlite:" + dbPath,
                     "--DB_PATH=" + dbPath);
    }

    private void cleanFiles(String... paths) {
        for (String p : paths) {
            new File(p).delete();
            new File(p + "-wal").delete();
            new File(p + "-shm").delete();
        }
    }
}