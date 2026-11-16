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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "qva.device-identity.enabled=true")
@AutoConfigureMockMvc
class DeviceIdentityIntegrationTest {
    private static final String BASE = "/api/v2/device-identities";
    private static final String PASSWORD = UUID.randomUUID().toString();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws IOException {
        var file = Files.createTempDirectory("qva-m62-test-").resolve("identity.sqlite");
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + file.toAbsolutePath());
    }

    private record Browser(long user, String device, MockHttpSession session, Cookie cookie) {
        MockHttpServletRequestBuilder use(MockHttpServletRequestBuilder request) {
            return request.session(session).cookie(cookie);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;

    private String hash;
    private Browser alice;
    private Browser bob;
    private String conversation;

    @BeforeEach
    void setup() throws Exception {
        hash = passwords.encode(PASSWORD);
        alice = account("APPROVED");
        bob = account("APPROVED");
        conversation = direct(alice, bob);
    }

    @Test
    void requiresAuthenticatedApprovedSessionAndSharedConversationWithoutHostBypass() throws Exception {
        mvc.perform(get(BASE + "/me")).andExpect(status().isUnauthorized());
        mvc.perform(alice.use(put(BASE + "/me/credentials/0").contentType(APPLICATION_OCTET_STREAM)
                .content(key()))).andExpect(status().isForbidden());

        Browser pending = account("PENDING");
        Browser revoked = account("APPROVED");
        Browser host = account("APPROVED");
        db.update("INSERT INTO qva_host_grants(slot,user_id,grant_id) VALUES (1,?,?)", host.user(), UUID.randomUUID().toString());
        register(alice, 0, key()).andExpect(status().isCreated());
        register(bob, 0, key()).andExpect(status().isCreated());
        register(revoked, 0, key()).andExpect(status().isCreated());
        db.update("UPDATE qva_devices SET status='REVOKED' WHERE id=?", revoked.device());

        register(pending, 0, key()).andExpect(status().isForbidden());
        register(revoked, 1, key()).andExpect(status().isForbidden());
        publish(revoked, 1, payload()).andExpect(status().isForbidden());
        mvc.perform(pending.use(get(peerPath(conversation, bob)))).andExpect(status().isForbidden());
        mvc.perform(host.use(get(peerPath(conversation, bob)))).andExpect(status().isNotFound());
        claim(host, conversation, bob, 1, UUID.randomUUID()).andExpect(status().isNotFound());

        String deniedConversation = direct(alice, revoked);
        mvc.perform(alice.use(get(peerPath(deniedConversation, revoked)))).andExpect(status().isNotFound());
        mvc.perform(alice.use(get(BASE + "/me"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(alice.user()));
        mvc.perform(get(BASE + "/me").session(alice.session()).cookie(bob.cookie())).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(alice.session())).andExpect(status().isOk());
        mvc.perform(get(BASE + "/me").cookie(alice.cookie())).andExpect(status().isUnauthorized());
        db.update("DELETE FROM qva_host_grants WHERE user_id = ?", host.user());
    }

    @Test
    void verifiesPerDeviceAndInvalidatesConfirmationAndPackagesOnIdentityRotation() throws Exception {
        byte[] original = key();
        register(alice, 0, key()).andExpect(status().isCreated());
        JsonNode first = body(register(bob, 0, original).andExpect(status().isCreated()));
        register(alice, 1, original).andExpect(status().isConflict());
        register(bob, 0, key()).andExpect(status().isConflict());
        byte[] stalePackage = payload();
        publish(bob, 1, stalePackage).andExpect(status().isCreated());
        mvc.perform(alice.use(get(peerPath(conversation, bob)))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.verification").value("UNVERIFIED"))
                .andExpect(jsonPath("$.signingKey").value(Base64.getEncoder().encodeToString(original)));
        claim(alice, conversation, bob, 1, UUID.randomUUID()).andExpect(status().isConflict());
        verify(alice, conversation, bob, 1, "0".repeat(64)).andExpect(status().isConflict());
        verify(alice, conversation, bob, 1, first.get("fingerprint").asText())
                .andExpect(status().isOk()).andExpect(jsonPath("$.verification").value("VERIFIED"));

        JsonNode second = body(register(bob, 1, key()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(2)));
        mvc.perform(alice.use(get(peerPath(conversation, bob)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.verification").value("KEY_CHANGED"));
        verify(alice, conversation, bob, 1, first.get("fingerprint").asText()).andExpect(status().isConflict());
        claim(alice, conversation, bob, 1, UUID.randomUUID()).andExpect(status().isConflict());
        publish(bob, 1, payload()).andExpect(status().isConflict());
        publish(bob, 2, stalePackage).andExpect(status().isConflict());

        verify(alice, conversation, bob, 2, second.get("fingerprint").asText())
                .andExpect(status().isOk()).andExpect(jsonPath("$.verification").value("VERIFIED"));
        register(alice, 1, key()).andExpect(status().isCreated());
        mvc.perform(alice.use(get(peerPath(conversation, bob)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.verification").value("UNVERIFIED"));
    }

    @Test
    void claimsOnlyFromVerifiedCurrentOwnerAndRejectsReplayWithoutBurningAnotherPackage() throws Exception {
        trustBob();
        byte[] first = payload();
        String firstId = body(publish(bob, 1, first).andExpect(status().isCreated())).get("id").asText();
        String secondId = body(publish(bob, 1, payload()).andExpect(status().isCreated())).get("id").asText();
        publish(alice, 1, first).andExpect(status().isConflict());
        publish(bob, 1, first).andExpect(status().isConflict());
        claim(alice, conversation, alice, 1, UUID.randomUUID()).andExpect(status().isNotFound());
        UUID request = UUID.randomUUID();
        JsonNode claim = body(claim(alice, conversation, bob, 1, request).andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value(bob.device())).andExpect(jsonPath("$.identityVersion").value(1)));
        assertThat(claim.get("id").asText()).isIn(firstId, secondId);
        claim(alice, conversation, bob, 1, request).andExpect(status().isConflict());
        claim(alice, conversation, bob, 1, UUID.randomUUID()).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(claim.get("id").asText().equals(firstId) ? secondId : firstId));
        claim(alice, conversation, bob, 1, UUID.randomUUID()).andExpect(status().isConflict());
        assertThat(db.queryForObject("""
                SELECT COUNT(*) FROM qva_device_key_packages
                WHERE device_id=? AND claimed_by=? AND package_bytes IS NULL
                """, Integer.class, bob.device(), alice.device())).isEqualTo(2);
    }

    @Test
    void concurrentRequestsClaimExactlyOnePackage() throws Exception {
        trustBob();
        byte[] packet = payload();
        String id = body(publish(bob, 1, packet).andExpect(status().isCreated())).get("id").asText();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<org.springframework.test.web.servlet.MvcResult> run = () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start signal timed out");
                return claim(alice, conversation, bob, 1, UUID.randomUUID()).andReturn();
            };
            var left = executor.submit(run);
            var right = executor.submit(run);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var outcomes = java.util.List.of(left.get(15, TimeUnit.SECONDS), right.get(15, TimeUnit.SECONDS));
            assertThat(outcomes).extracting(result -> result.getResponse().getStatus()).containsExactlyInAnyOrder(200, 409);
            var winner = outcomes.stream().filter(result -> result.getResponse().getStatus() == 200).findFirst().orElseThrow();
            assertThat(json.readTree(winner.getResponse().getContentAsString()).get("keyPackage").binaryValue()).isEqualTo(packet);
        } finally {
            executor.shutdownNow();
        }
        assertThat(db.queryForObject("SELECT claimed_by FROM qva_device_key_packages WHERE id=?",
                String.class, id)).isEqualTo(alice.device());
        assertThat(db.queryForObject("SELECT package_bytes FROM qva_device_key_packages WHERE id=?",
                byte[].class, id)).isNull();
    }

    @Test
    void groupRemovalAndExpirationCloseTheClaimPath() throws Exception {
        trustBob();
        String group = UUID.randomUUID().toString();
        db.update("INSERT INTO qva_groups(id,name,owner_user_id,created_ms) VALUES (?,'Identity fixture',?,1)", group, alice.user());
        db.update("INSERT INTO qva_group_members(group_id,user_id,after_sequence) VALUES (?,?,0),(?,?,0)",
                group, alice.user(), group, bob.user());
        String expiredId = body(publish(bob, 1, payload()).andExpect(status().isCreated())).get("id").asText();
        db.update("UPDATE qva_device_key_packages SET published_ms=1,expires_ms=2 WHERE id=?", expiredId);
        claim(alice, group, bob, 1, UUID.randomUUID()).andExpect(status().isConflict());
        publish(bob, 1, payload()).andExpect(status().isCreated());
        claim(alice, group, bob, 1, UUID.randomUUID()).andExpect(status().isOk());
        publish(bob, 1, payload()).andExpect(status().isCreated());
        db.update("DELETE FROM qva_group_members WHERE group_id=? AND user_id=?", group, bob.user());
        claim(alice, group, bob, 1, UUID.randomUUID()).andExpect(status().isNotFound());
        db.update("UPDATE qva_devices SET status='REVOKED' WHERE id=?", bob.device());
        claim(alice, conversation, bob, 1, UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void enforcesByteLimitsAndPerDeviceActivePool() throws Exception {
        register(alice, 0, new byte[32]).andExpect(status().isBadRequest());
        register(alice, 0, new byte[44]).andExpect(status().isBadRequest());
        mvc.perform(alice.use(put(BASE + "/me/credentials/0").with(csrf()).content(key())))
                .andExpect(status().isUnsupportedMediaType());
        register(alice, 0, new byte[45]).andExpect(status().isPayloadTooLarge());
        register(alice, 0, key()).andExpect(status().isCreated());
        publish(alice, 1, new byte[0]).andExpect(status().isBadRequest());
        publish(alice, 1, new byte[16_385]).andExpect(status().isPayloadTooLarge());
        for (int i = 0; i < 32; i++) publish(alice, 1, payload()).andExpect(status().isCreated());
        publish(alice, 1, payload()).andExpect(status().isConflict());
    }

    private Browser account(String state) throws Exception {
        String name = "key_" + UUID.randomUUID().toString().replace("-", "");
        db.update("INSERT INTO users(username,display_name,password_hash) VALUES (?,'Identity fixture',?)", name, hash);
        long user = db.queryForObject("SELECT id FROM users WHERE username=?", Long.class, name);
        byte[] token = new byte[32];
        new java.security.SecureRandom().nextBytes(token);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(token);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.US_ASCII)));
        String device = UUID.randomUUID().toString();
        db.update("INSERT INTO qva_devices(id,user_id,name,credential_digest,status,registered_ms) VALUES (?,?,?,?,?,1)",
                device, user, "Identity test browser", digest, state);
        var login = mvc.perform(post("/api/auth/login").param("username", name).param("password", PASSWORD))
                .andExpect(status().isOk()).andReturn();
        return new Browser(user, device, (MockHttpSession) Objects.requireNonNull(login.getRequest().getSession(false)),
                new Cookie("QVA_DEVICE", raw));
    }

    private String direct(Browser left, Browser right) {
        String id = UUID.randomUUID().toString();
        db.update("INSERT INTO qva_direct_conversations(id,lower_user_id,upper_user_id,created_ms) VALUES (?,?,?,1)",
                id, Math.min(left.user(), right.user()), Math.max(left.user(), right.user()));
        return id;
    }

    private void trustBob() throws Exception {
        register(alice, 0, key()).andExpect(status().isCreated());
        JsonNode peer = body(register(bob, 0, key()).andExpect(status().isCreated()));
        verify(alice, conversation, bob, 1, peer.get("fingerprint").asText()).andExpect(status().isOk());
    }

    private ResultActions register(Browser owner, int expected, byte[] bytes) throws Exception {
        return mvc.perform(owner.use(put(BASE + "/me/credentials/" + expected).with(csrf())
                .contentType(APPLICATION_OCTET_STREAM).content(bytes)));
    }

    private ResultActions publish(Browser owner, int version, byte[] bytes) throws Exception {
        return mvc.perform(owner.use(post(BASE + "/me/credentials/" + version + "/key-packages").with(csrf())
                .contentType(APPLICATION_OCTET_STREAM).content(bytes)));
    }

    private ResultActions verify(Browser verifier, String conversation, Browser peer, int version, String fingerprint) throws Exception {
        return mvc.perform(verifier.use(put(peerPath(conversation, peer) + "/verification/" + version).with(csrf())
                .header("QVA-Identity-Fingerprint", fingerprint)));
    }

    private ResultActions claim(Browser claimant, String conversation, Browser peer, int version, UUID request) throws Exception {
        return mvc.perform(claimant.use(post(peerPath(conversation, peer) + "/key-packages/" + version + "/claim")
                .with(csrf()).header("Idempotency-Key", request)));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static String peerPath(String conversation, Browser peer) {
        return BASE + "/conversations/" + conversation + "/devices/" + peer.device();
    }

    private static byte[] key() throws java.security.GeneralSecurityException {
        return java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic().getEncoded();
    }

    private static byte[] payload() {
        return ("synthetic-public-package-" + UUID.randomUUID()).getBytes(StandardCharsets.US_ASCII);
    }
}
