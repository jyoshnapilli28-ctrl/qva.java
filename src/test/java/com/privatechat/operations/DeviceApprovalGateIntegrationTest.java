package com.privatechat.operations;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DeviceApprovalGateIntegrationTest {
    private static final String PASSWORD = UUID.randomUUID().toString();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws IOException {
        var file = Files.createTempDirectory("qva-device-gate-test-").resolve("gate.sqlite");
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + file.toAbsolutePath());
    }

    record Browser(MockHttpSession session, Cookie cookie) {
        MockHttpServletRequestBuilder use(MockHttpServletRequestBuilder request) {
            return request.session(session).cookie(cookie);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;

    @Test
    void classicRestAndWebSocketHandshakeRequireApprovedDeviceAndRespectRevocation() throws Exception {
        String username = "gate_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        db.update("INSERT INTO users(username,display_name,password_hash) VALUES (?,?,?)",
                username, "Gate test", passwords.encode(PASSWORD));
        long userId = db.queryForObject("SELECT id FROM users WHERE username=?", Long.class, username);
        String raw = "G".repeat(43);
        String deviceId = UUID.randomUUID().toString();
        db.update("INSERT INTO qva_devices(id,user_id,name,credential_digest,status,registered_ms) VALUES (?,?,?,?,?,?)",
                deviceId, userId, "Gate test browser", digest(raw), "PENDING", System.currentTimeMillis());
        var login = mvc.perform(post("/api/auth/login").param("username", username).param("password", PASSWORD))
                .andExpect(status().isOk()).andReturn();
        var browser = new Browser((MockHttpSession) Objects.requireNonNull(login.getRequest().getSession(false)),
                new Cookie("QVA_DEVICE", raw));

        mvc.perform(browser.use(get("/api/rooms/general/messages"))).andExpect(status().isForbidden());
        mvc.perform(browser.use(get("/api/users"))).andExpect(status().isForbidden());
        mvc.perform(browser.use(get("/ws/chat").header("Upgrade", "websocket").header("Connection", "Upgrade")))
                .andExpect(status().isForbidden());

        db.update("UPDATE qva_devices SET status='APPROVED',approved_ms=? WHERE id=?", System.currentTimeMillis(), deviceId);
        mvc.perform(browser.use(get("/api/rooms/general/messages"))).andExpect(status().isOk());
        mvc.perform(browser.use(get("/api/users"))).andExpect(status().isOk());
        mvc.perform(browser.use(get("/ws/chat").header("Upgrade", "websocket").header("Connection", "Upgrade")))
                .andExpect(status().isUpgradeRequired()); // Spring reached the handshake and rejected the missing WebSocket version.

        db.update("UPDATE qva_devices SET status='REVOKED',revoked_ms=? WHERE id=?", System.currentTimeMillis(), deviceId);
        mvc.perform(browser.use(get("/api/rooms/general/messages"))).andExpect(status().isForbidden());
        mvc.perform(browser.use(get("/api/users"))).andExpect(status().isForbidden());
        mvc.perform(browser.use(get("/ws/chat").header("Upgrade", "websocket").header("Connection", "Upgrade")))
                .andExpect(status().isForbidden());
    }

    private static String digest(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
    }
}
