package com.privatechat;

import com.privatechat.repository.ChatRoomRepository;
import com.privatechat.repository.MessageRepository;
import com.privatechat.repository.UserRepository;
import com.privatechat.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

/**
 * Integration tests for the chat application.
 *
 * Uses a file-based test SQLite database (target/test-integration.db).
 * Flyway migrates and seeds the database on first run.
 * Tests use relative assertions to handle accumulated test data.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:sqlite:./target/test-integration.db",
    "spring.flyway.enabled=true",
    "spring.flyway.clean-disabled=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "logging.level.com.privatechat=WARN",
    "logging.level.org.springframework.security=WARN"
})
class PrivateChatIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private MessageService messageService;

    // Mock the WebSocket broker so tests don't need a running STOMP broker
    @MockBean
    private SimpMessagingTemplate simpMessagingTemplate;

    @BeforeEach
    void setUp() {
        doNothing().when(simpMessagingTemplate).convertAndSend(anyString(), (Object) any());
    }

    // --------------------------------------------------------
    // User tests
    // --------------------------------------------------------

    @Test
    void testFourUsersExist() {
        List<com.privatechat.model.User> users = userRepository.findByIsActiveTrue();
        assertThat(users).hasSize(4);
        List<String> names = users.stream().map(com.privatechat.model.User::getUsername).toList();
        assertThat(names).containsExactlyInAnyOrder("jyoshna", "meghana", "yash", "yashuu");
    }

    @Test
    void testUserPasswordHashesAreStored() {
        for (String username : List.of("jyoshna", "meghana", "yash", "yashuu")) {
            com.privatechat.model.User user = userRepository.findByUsername(username).orElseThrow();
            assertThat(user.getPasswordHash()).isNotNull();
            assertThat(user.getPasswordHash()).startsWith("$2a$");
        }
    }

    @Test
    void testUsernamesAreUnique() {
        assertThat(userRepository.existsByUsername("jyoshna")).isTrue();
        assertThat(userRepository.existsByUsernameIgnoreCase("JyOshNa")).isTrue();
        assertThat(userRepository.existsByUsername("alice")).isFalse();
        assertThat(userRepository.existsByUsername("nobody")).isFalse();
    }

    // --------------------------------------------------------
    // Chat room tests
    // --------------------------------------------------------

    @Test
    void testGeneralRoomExists() {
        assertThat(chatRoomRepository.findByName("general")).isPresent();
    }

    // --------------------------------------------------------
    // Message persistence tests
    // --------------------------------------------------------

    @Test
    void testMessagePersistence() {
        long before = messageRepository.countByRoomId(
                chatRoomRepository.findByName("general").orElseThrow().getId());
        messageService.sendMessage("jyoshna", "general", "Hello, integration test!");
        long after = messageRepository.countByRoomId(
                chatRoomRepository.findByName("general").orElseThrow().getId());
        assertThat(after).isEqualTo(before + 1);
    }

    @Test
    void testMessageContentIsStored() {
        String unique = "IntegTestMsg-" + System.nanoTime();
        messageService.sendMessage("jyoshna", "general", unique);
        var history = messageService.getHistory("general");
        assertThat(history).anyMatch(m -> m.getContent().equals(unique));
    }

    @Test
    void testMessageHasTimestamp() {
        messageService.sendMessage("meghana", "general", "TimestampTest-" + System.nanoTime());
        var history = messageService.getHistory("general");
        var last = history.get(history.size() - 1);
        assertThat(last.getSentAt()).isNotNull();
    }

    @Test
    void testMessageSenderIdentity() {
        String tag = "FromYash-" + System.nanoTime();
        messageService.sendMessage("yash", "general", tag);
        var history = messageService.getHistory("general");
        var msg = history.stream().filter(m -> m.getContent().equals(tag)).findFirst().orElseThrow();
        assertThat(msg.getUsername()).isEqualTo("yash");
        assertThat(msg.getDisplayName()).isEqualTo("Yash");
    }

    @Test
    void testHistoryIsOrderedChronologically() {
        // Use unique markers to find our messages in history
        String a = "Order-A-" + System.nanoTime();
        String b = "Order-B-" + (System.nanoTime() + 1);
        String c = "Order-C-" + (System.nanoTime() + 2);
        messageService.sendMessage("jyoshna", "general", a);
        messageService.sendMessage("meghana", "general", b);
        messageService.sendMessage("yash", "general", c);

        var history = messageService.getHistory("general");
        int idxA = -1, idxB = -1, idxC = -1;
        for (int i = 0; i < history.size(); i++) {
            String ct = history.get(i).getContent();
            if (ct.equals(a)) idxA = i;
            if (ct.equals(b)) idxB = i;
            if (ct.equals(c)) idxC = i;
        }
        assertThat(idxA).isGreaterThanOrEqualTo(0);
        assertThat(idxB).isGreaterThan(idxA);
        assertThat(idxC).isGreaterThan(idxB);
    }

    // --------------------------------------------------------
    // Validation tests
    // --------------------------------------------------------

    @Test
    void testEmptyMessageRejected() {
        assertThatThrownBy(() -> messageService.sendMessage("jyoshna", "general", ""))
                .hasMessageContaining("empty");
    }

    @Test
    void testBlankMessageRejected() {
        assertThatThrownBy(() -> messageService.sendMessage("jyoshna", "general", "   "))
                .hasMessageContaining("empty");
    }

    @Test
    void testMessageTooLongRejected() {
        String longContent = "x".repeat(2001);
        assertThatThrownBy(() -> messageService.sendMessage("jyoshna", "general", longContent))
                .hasMessageContaining("maximum length");
    }

    @Test
    void testUnknownRoomRejected() {
        assertThatThrownBy(() -> messageService.sendMessage("jyoshna", "nonexistent", "hello"))
                .hasMessageContaining("not found");
    }

    @Test
    void testUnknownUserRejected() {
        assertThatThrownBy(() -> messageService.sendMessage("ghost", "general", "hello"))
                .hasMessageContaining("not found");
    }

    // --------------------------------------------------------
    // History retrieval
    // --------------------------------------------------------

    @Test
    void testHistoryLimitRespected() {
        // Send 5 messages
        for (int i = 1; i <= 5; i++) {
            messageService.sendMessage("jyoshna", "general", "LimitTest-" + i + "-" + System.nanoTime());
        }
        var history = messageService.getHistory("general", 3);
        assertThat(history).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    void testMultipleUserMessagesInHistory() {
        String ta = "Jyoshna-" + System.nanoTime();
        String tb = "Meghana-" + System.nanoTime();
        String td = "Yashuu-" + System.nanoTime();
        messageService.sendMessage("jyoshna", "general", ta);
        messageService.sendMessage("meghana", "general", tb);
        messageService.sendMessage("yashuu", "general", td);

        var history = messageService.getHistory("general");
        assertThat(history).anyMatch(m -> m.getContent().equals(ta) && m.getUsername().equals("jyoshna"));
        assertThat(history).anyMatch(m -> m.getContent().equals(tb) && m.getUsername().equals("meghana"));
        assertThat(history).anyMatch(m -> m.getContent().equals(td) && m.getUsername().equals("yashuu"));
    }
}
