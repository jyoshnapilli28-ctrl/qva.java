package com.privatechat;

import com.privatechat.dto.MessageDto;
import com.privatechat.service.MessageService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.File;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Restart-persistence test using a disposable SQLite database file.
 *
 * Simulates:
 * 1. Server Start: Migrates schema and seeds users in a disposable DB file.
 * 2. Message Activity: User sends messages.
 * 3. Server Shutdown: Spring context is closed cleanly.
 * 4. Server Restart: New Spring context boots against the existing DB file.
 * 5. Verification: History and accounts persist across the restart.
 */
class RestartPersistenceTest {

    private static final String TEST_DB_PATH = "./target/disposable-restart-test.db";

    @Test
    void testMessagesSurviveServerShutdownAndRestart() {
        // Clean up any previous test file
        File dbFile = new File(TEST_DB_PATH);
        if (dbFile.exists()) {
            dbFile.delete();
        }

        String uniqueTag1 = "PreShutdownMsg-" + System.nanoTime();
        String uniqueTag2 = "SecondMsg-" + System.nanoTime();

        // -------------------------------------------------------------
        // RUN 1: Start server, send messages, shut down
        // -------------------------------------------------------------
        ConfigurableApplicationContext ctx1 = new SpringApplicationBuilder(PrivateChatApplication.class)
                .properties(
                        "server.port=0",
                        "DB_PATH=" + TEST_DB_PATH,
                        "spring.datasource.url=jdbc:sqlite:" + TEST_DB_PATH,
                        "spring.flyway.enabled=true",
                        "spring.flyway.clean-disabled=false",
                        "spring.jpa.hibernate.ddl-auto=none",
                        "logging.level.com.privatechat=WARN",
                        "logging.level.org.springframework=WARN"
                )
                .run("--spring.datasource.url=jdbc:sqlite:" + TEST_DB_PATH,
                     "--DB_PATH=" + TEST_DB_PATH,
                     "--server.port=0");

        try {
            MessageService msgService1 = ctx1.getBean(MessageService.class);
            msgService1.sendMessage("jyoshna", "general", uniqueTag1);
            msgService1.sendMessage("meghana", "general", uniqueTag2);

            List<MessageDto> history1 = msgService1.getHistory("general");
            assertThat(history1).anyMatch(m -> m.getContent().equals(uniqueTag1));
            assertThat(history1).anyMatch(m -> m.getContent().equals(uniqueTag2));
        } finally {
            // Graceful shutdown
            ctx1.close();
        }

        // Verify the SQLite file exists on disk after shutdown
        assertThat(dbFile.exists()).isTrue();
        assertThat(dbFile.length()).isGreaterThan(0);

        // -------------------------------------------------------------
        // RUN 2: Restart server pointing at the same database file
        // -------------------------------------------------------------
        ConfigurableApplicationContext ctx2 = new SpringApplicationBuilder(PrivateChatApplication.class)
                .properties(
                        "server.port=0",
                        "DB_PATH=" + TEST_DB_PATH,
                        "spring.datasource.url=jdbc:sqlite:" + TEST_DB_PATH,
                        "spring.flyway.enabled=true",
                        "spring.flyway.clean-disabled=false",
                        "spring.jpa.hibernate.ddl-auto=none",
                        "logging.level.com.privatechat=WARN",
                        "logging.level.org.springframework=WARN"
                )
                .run("--spring.datasource.url=jdbc:sqlite:" + TEST_DB_PATH,
                     "--DB_PATH=" + TEST_DB_PATH,
                     "--server.port=0");

        try {
            MessageService msgService2 = ctx2.getBean(MessageService.class);

            // Verify prior messages survived the restart
            List<MessageDto> history2 = msgService2.getHistory("general");
            assertThat(history2).anyMatch(m -> m.getContent().equals(uniqueTag1) && m.getUsername().equals("jyoshna"));
            assertThat(history2).anyMatch(m -> m.getContent().equals(uniqueTag2) && m.getUsername().equals("meghana"));

            // Verify new messages can be added after restart
            String postRestartMsg = "PostRestartMsg-" + System.nanoTime();
            msgService2.sendMessage("yash", "general", postRestartMsg);

            List<MessageDto> updatedHistory = msgService2.getHistory("general");
            assertThat(updatedHistory).anyMatch(m -> m.getContent().equals(postRestartMsg) && m.getUsername().equals("yash"));
            assertThat(updatedHistory).anyMatch(m -> m.getContent().equals(uniqueTag1));
        } finally {
            ctx2.close();
            // Clean up disposable test file
            dbFile.delete();
            new File(TEST_DB_PATH + "-wal").delete();
            new File(TEST_DB_PATH + "-shm").delete();
        }
    }
}