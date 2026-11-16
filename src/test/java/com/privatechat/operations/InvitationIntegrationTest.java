package com.privatechat.operations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class InvitationIntegrationTest {
    private static final String HOST_PASSWORD = UUID.randomUUID().toString();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws IOException {
        var file = Files.createTempDirectory("qva-invitations-test-").resolve("invitations.sqlite");
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + file.toAbsolutePath());
    }

    record Host(MockHttpSession session, Cookie deviceCookie) {
        MockHttpServletRequestBuilder use(MockHttpServletRequestBuilder request) {
            return request.session(session).cookie(deviceCookie);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;

    private long hostId;
    private Host host;

    @BeforeEach
    void setUpHost() throws Exception {
        db.update("DELETE FROM qva_host_grants");
        String hostName = "host_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        db.update("INSERT INTO users(username,display_name,password_hash) VALUES (?,?,?)",
                hostName, "Invitation test host", passwords.encode(HOST_PASSWORD));
        hostId = db.queryForObject("SELECT id FROM users WHERE username=?", Long.class, hostName);
        byte[] deviceSecret = new byte[32];
        new SecureRandom().nextBytes(deviceSecret);
        String rawDevice = Base64.getUrlEncoder().withoutPadding().encodeToString(deviceSecret);
        String deviceId = UUID.randomUUID().toString();
        db.update("INSERT INTO qva_devices(id,user_id,name,credential_digest,status,registered_ms) VALUES (?,?,?,?,?,?)",
                deviceId, hostId, "Approved test host", digest(rawDevice), "APPROVED", System.currentTimeMillis());
        db.update("INSERT INTO qva_host_grants(slot,user_id,grant_id) VALUES (1,?,?)", hostId, UUID.randomUUID().toString());
        var response = mvc.perform(post("/api/auth/login").param("username", hostName).param("password", HOST_PASSWORD))
                .andExpect(status().isOk()).andReturn();
        host = new Host((MockHttpSession) Objects.requireNonNull(response.getRequest().getSession(false)),
                new Cookie("QVA_DEVICE", rawDevice));
    }

    @Test
    void invitationIsHashedSingleUseMemberOnlyAndRevocable() throws Exception {
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
        var created = mvc.perform(host.use(post("/api/v2/host/invitations").with(csrf())
                        .contentType(APPLICATION_JSON).content("{\"expirationHours\":24}")))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.token").value(org.hamcrest.Matchers.matchesPattern("[A-Za-z0-9_-]{43}")))
                .andReturn();
        JsonNode invitation = json.readTree(created.getResponse().getContentAsString());
        String token = invitation.get("token").asText();
        assertThat(db.queryForObject("SELECT token_hash FROM qva_invitations WHERE id=?", String.class,
                invitation.get("id").asText())).isEqualTo(digest(token));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM qva_invitations WHERE token_hash=?", Integer.class, token)).isZero();

        String body = json.writeValueAsString(new RegistrationRequest(token, "new_member", "New Member", "a-long-password-123"));
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.username").value("new_member"));
        long memberId = db.queryForObject("SELECT id FROM users WHERE username='new_member'", Long.class);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM qva_host_grants WHERE user_id=?", Integer.class, memberId)).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM qva_devices WHERE user_id=?", Integer.class, memberId)).isZero();
        assertThat(db.queryForObject("SELECT redeemed_user_id FROM qva_invitations WHERE id=?", Long.class,
                invitation.get("id").asText())).isEqualTo(memberId);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM qva_invitation_audit WHERE invitation_id=?", Integer.class,
                invitation.get("id").asText())).isEqualTo(2);

        mvc.perform(post("/api/auth/register").with(csrf()).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM users WHERE username='new_member'", Integer.class)).isEqualTo(1);

        var second = mvc.perform(host.use(post("/api/v2/host/invitations").with(csrf())
                        .contentType(APPLICATION_JSON).content("{\"expirationHours\":24}")))
                .andExpect(status().isCreated()).andReturn();
        String secondId = json.readTree(second.getResponse().getContentAsString()).get("id").asText();
        String secondToken = json.readTree(second.getResponse().getContentAsString()).get("token").asText();
        mvc.perform(host.use(delete("/api/v2/host/invitations/" + secondId).with(csrf())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revoked").value(true));
        String revokedBody = json.writeValueAsString(new RegistrationRequest(secondToken, "other_member", "Other Member", "another-long-password-123"));
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(APPLICATION_JSON).content(revokedBody))
                .andExpect(status().isConflict());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM users WHERE username='other_member'", Integer.class)).isZero();
    }

    private record RegistrationRequest(String token, String username, String displayName, String password) {}

    @Test
    void onlyOneConcurrentRequestCanRedeemAnInvitation() throws Exception {
        var result = mvc.perform(host.use(post("/api/v2/host/invitations").with(csrf())))
                .andExpect(status().isCreated()).andReturn();
        String token = json.readTree(result.getResponse().getContentAsString()).get("token").asText();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> registerTogether("race_first", token, ready, start));
            var second = pool.submit(() -> registerTogether("race_second", token, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(new Integer[]{first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)})
                    .containsExactlyInAnyOrder(201, 409);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM users WHERE username IN ('race_first', 'race_second')",
                    Integer.class)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private int registerTogether(String username, String token, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        String body = json.writeValueAsString(new RegistrationRequest(token, username, "Test Member", HOST_PASSWORD));
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Registration did not start");
        return mvc.perform(post("/api/auth/register").with(csrf()).with(request -> {
                    request.setRemoteAddr("192.0.2.10");
                    return request;
                }).contentType(APPLICATION_JSON).content(body))
                .andReturn().getResponse().getStatus();
    }

    private static String digest(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.US_ASCII)));
    }
}
