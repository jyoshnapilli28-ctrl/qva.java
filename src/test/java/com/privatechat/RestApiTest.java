package com.privatechat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * REST API endpoint tests using MockMvc.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:sqlite:./target/test-api.db",
    "spring.flyway.enabled=true",
    "spring.flyway.clean-disabled=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "logging.level.com.privatechat=WARN",
    "logging.level.org.springframework.security=WARN"
})
class RestApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.privatechat.repository.UserRepository userRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate database;

    private final Cookie approvedDevice = new Cookie("QVA_DEVICE", "R".repeat(43));

    @BeforeEach
    void addApprovedDeviceFixture() throws Exception {
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(approvedDevice.getValue().getBytes(StandardCharsets.US_ASCII)));
        database.update("""
                INSERT OR IGNORE INTO qva_devices(id,user_id,name,credential_digest,status,registered_ms)
                SELECT 'rest-api-fixture',id,'REST fixture browser',?,'APPROVED',1 FROM users WHERE username='jyoshna'
                """, digest);
    }

    // Mock the WebSocket broker so tests don't require a running STOMP broker
    @MockBean
    private SimpMessagingTemplate simpMessagingTemplate;

    // --------------------------------------------------------
    // Auth endpoints
    // --------------------------------------------------------

    @Test
    void authStatusPublic() throws Exception {
        mockMvc.perform(get("/api/auth/status"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void uninitializedAccountLoginBlocked() throws Exception {
        // jyoshna begins with the uninitialized default placeholder hash
        // Login must be blocked until an operator runs Set-Password.bat
        mockMvc.perform(post("/api/auth/login")
                .param("username", "jyoshna")
                .param("password", "chat1234"))
               .andExpect(status().isUnauthorized())
               .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void oldUsernameLoginRejected() throws Exception {
        // Predefined accounts like "alice" must not be authenticatable
        mockMvc.perform(post("/api/auth/login")
                .param("username", "alice")
                .param("password", "chat1234"))
               .andExpect(status().isUnauthorized())
               .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void loginWithInvalidCredentialsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .param("username", "jyoshna")
                .param("password", "wrongpassword"))
               .andExpect(status().isUnauthorized())
               .andExpect(jsonPath("$.success").value(false))
               .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void loginWithInitializedCredentialsSucceeds() throws Exception {
        // Initialize user password
        com.privatechat.model.User user = userRepository.findByUsername("jyoshna").orElseThrow();
        user.setPasswordHash(passwordEncoder.encode("SecurePass123!#"));
        userRepository.save(user);

        mockMvc.perform(post("/api/auth/login")
                .param("username", "jyoshna")
                .param("password", "SecurePass123!#"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.success").value(true))
               .andExpect(jsonPath("$.username").value("jyoshna"));
    }

    @Test
    void loginCaseInsensitiveSucceeds() throws Exception {
        // Initialize user password
        com.privatechat.model.User user = userRepository.findByUsername("jyoshna").orElseThrow();
        user.setPasswordHash(passwordEncoder.encode("SecurePass123!#"));
        userRepository.save(user);

        // Attempt login with mixed-case username
        mockMvc.perform(post("/api/auth/login")
                .param("username", "JyOsHnA")
                .param("password", "SecurePass123!#"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.success").value(true))
               .andExpect(jsonPath("$.username").value("jyoshna"));
    }

    @Test
    void logoutSucceeds() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void authMeRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "jyoshna", roles = "USER")
    void authMeReturnsUser() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.username").value("jyoshna"))
               .andExpect(jsonPath("$.authenticated").value(true));
    }

    // --------------------------------------------------------
    // User endpoints
    // --------------------------------------------------------

    @Test
    void usersRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/users"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "jyoshna", roles = "USER")
    void usersListReturnsFourUsers() throws Exception {
        mockMvc.perform(get("/api/users").cookie(approvedDevice))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.length()").value(4));
    }

    // --------------------------------------------------------
    // Message endpoints
    // --------------------------------------------------------

    @Test
    void messagesRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/rooms/general/messages"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "jyoshna", roles = "USER")
    void messagesHistoryReturnsOk() throws Exception {
        mockMvc.perform(get("/api/rooms/general/messages").cookie(approvedDevice))
               .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "jyoshna", roles = "USER")
    void unknownRoomReturns400() throws Exception {
        mockMvc.perform(get("/api/rooms/doesnotexist/messages").cookie(approvedDevice))
               .andExpect(status().isBadRequest());
    }

    // --------------------------------------------------------
    // Health (public endpoint)
    // --------------------------------------------------------

    @Test
    void healthEndpointReturnsUp() throws Exception {
        mockMvc.perform(get("/api/health"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.status").value("UP"));
    }

    // --------------------------------------------------------
    // Static frontend served
    // --------------------------------------------------------

    @Test
    void indexHtmlIsPublic() throws Exception {
        mockMvc.perform(get("/index.html"))
               .andExpect(status().isOk());
    }

    @Test
    void rootRedirectsToIndex() throws Exception {
        mockMvc.perform(get("/"))
               .andExpect(status().isOk());
    }
}
