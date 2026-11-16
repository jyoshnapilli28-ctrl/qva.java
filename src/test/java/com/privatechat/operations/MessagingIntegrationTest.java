package com.privatechat.operations;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "qva.messaging.enabled=true")
@AutoConfigureMockMvc
class MessagingIntegrationTest {
    private static final String PASSWORD = UUID.randomUUID().toString();
    private static final String BASE = "/api/v2/conversations";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws IOException {
        var file = Files.createTempDirectory("qva-m5-test-").resolve("fixture.sqlite");
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + file.toAbsolutePath());
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    @Autowired MessageLifecycle lifecycle;

    record Browser(MockHttpSession session, Cookie cookie, String deviceId) {
        MockHttpServletRequestBuilder use(MockHttpServletRequestBuilder method) {
            return method.session(session).cookie(cookie);
        }
    }

    @BeforeEach
    void fixture() {
        String hash = passwords.encode(PASSWORD);
        jdbc.update("INSERT INTO users(id,username,display_name,password_hash) VALUES (810001,'fixture_a','Member A',?)", hash);
        jdbc.update("INSERT INTO users(id,username,display_name,password_hash) VALUES (810002,'fixture_b','Member B',?)", hash);
        jdbc.update("INSERT INTO users(id,username,display_name,password_hash) VALUES (810003,'fixture_c','Member C',?)", hash);
    }

    private Browser login(String username, long user, char marker, String state) throws Exception {
        String raw = String.valueOf(marker).repeat(43);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(StandardCharsets.US_ASCII)));
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO qva_devices(id,user_id,name,credential_digest,status,registered_ms) VALUES (?,?,?,?,?,?)",
                id, user, "Test browser", digest, state, System.currentTimeMillis());
        var result = mvc.perform(post("/api/auth/login").param("username", username).param("password", PASSWORD))
                .andExpect(status().isOk()).andReturn();
        return new Browser((MockHttpSession) Objects.requireNonNull(result.getRequest().getSession(false)),
                new Cookie("QVA_DEVICE", raw), id);
    }

    private String open(Browser browser, long peer) throws Exception {
        var result = mvc.perform(browser.use(post(BASE + "/direct").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"peerId\":" + peer + "}")))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private MockHttpServletRequestBuilder submit(Browser browser, String resource, String key, byte[] data) {
        return browser.use(post(resource).with(csrf()).contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("Idempotency-Key", key).header("QVA-Envelope-Version", 1).content(data));
    }

    @Test
    void approvedPeersRetrieveOpaqueEnvelopesAndRetriesRemainIdempotent() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        jdbc.update("INSERT INTO qva_host_grants(slot,user_id,grant_id) VALUES (1,810001,?)",
                UUID.randomUUID().toString());
        Browser sender = login("fixture_a", 810001, 'a', "APPROVED");
        Browser receiver = login("fixture_b", 810002, 'b', "APPROVED");
        Browser other = login("fixture_c", 810003, 'c', "PENDING");
        String conversation = open(sender, 810002);
        assertThat(open(receiver, 810001)).isEqualTo(conversation);
        mvc.perform(receiver.use(get(BASE))).andExpect(jsonPath("$[0].id").value(conversation));

        String history = BASE + "/" + conversation + "/envelopes";
        String key = UUID.randomUUID().toString();
        byte[] bytes = {0, -1, 4, 0};
        mvc.perform(submit(sender, history, key, bytes)).andExpect(status().isOk())
                .andExpect(jsonPath("$.senderUserId").value(810001));
        mvc.perform(submit(sender, history, key, bytes)).andExpect(status().isOk());
        mvc.perform(submit(sender, history, key, new byte[]{2})).andExpect(status().isConflict());
        mvc.perform(submit(sender, history, UUID.randomUUID().toString(), new byte[16385]))
                .andExpect(status().isPayloadTooLarge());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM qva_message_envelopes", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT envelope FROM qva_message_envelopes", byte[].class)).isEqualTo(bytes);

        mvc.perform(receiver.use(get(history))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].envelope").value("AP8EAA=="));
        mvc.perform(receiver.use(get(history).param("before", "1"))).andExpect(content().json("[]"));
        mvc.perform(other.use(get(history))).andExpect(status().isForbidden());
        jdbc.update("UPDATE qva_devices SET status='APPROVED' WHERE id=?", other.deviceId());
        mvc.perform(other.use(get(history))).andExpect(status().isNotFound());
        String another = open(other, 810002);
        mvc.perform(sender.use(get(BASE + "/" + another + "/envelopes"))).andExpect(status().isNotFound());
        mvc.perform(submit(other, history, UUID.randomUUID().toString(), bytes)).andExpect(status().isNotFound());
        String directReceipt = history + "/1/receipt";
        mvc.perform(receiver.use(post(directReceipt).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"DELIVERED\"}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.deliveredAt").exists());
        mvc.perform(receiver.use(post(directReceipt).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"READ\"}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.readAt").exists());
        mvc.perform(sender.use(get(directReceipt))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].readAt").exists());
        mvc.perform(other.use(get(directReceipt))).andExpect(status().isNotFound());

        var groupResponse = mvc.perform(sender.use(post(BASE + "/groups").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Fixture group\"}")))
                .andExpect(status().isOk()).andReturn();
        String group = json.readTree(groupResponse.getResponse().getContentAsString()).get("id").asText();
        String members = BASE + "/groups/" + group + "/participants";
        String groupHistory = BASE + "/" + group + "/envelopes";
        mvc.perform(submit(sender, groupHistory, UUID.randomUUID().toString(), bytes)).andExpect(status().isOk());
        mvc.perform(sender.use(post(members).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":810002}"))).andExpect(status().isOk());
        mvc.perform(receiver.use(get(groupHistory))).andExpect(content().json("[]"));
        mvc.perform(receiver.use(post(members).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":810003}"))).andExpect(status().isNotFound());
        mvc.perform(submit(sender, groupHistory, key, bytes)).andExpect(status().isConflict());
        mvc.perform(submit(receiver, groupHistory, UUID.randomUUID().toString(), bytes)).andExpect(status().isOk());
        mvc.perform(receiver.use(get(groupHistory).param("after", "1"))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sequence").value(2));
        mvc.perform(sender.use(delete(members + "/810002").with(csrf()))).andExpect(status().isOk());
        mvc.perform(receiver.use(get(groupHistory))).andExpect(status().isNotFound());

        jdbc.update("UPDATE qva_message_envelopes SET accepted_ms=0 WHERE conversation_id=?", conversation);
        mvc.perform(sender.use(get(history))).andExpect(content().json("[]"));
        lifecycle.cleanup();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM qva_message_receipts", Integer.class)).isZero();
        mvc.perform(submit(sender, history, key, bytes)).andExpect(status().isConflict());

        for (int i = 0; i < 55; i++) {
            jdbc.update("""
                    INSERT INTO qva_message_envelopes
                    (conversation_id,sender_user_id,sender_device_id,request_id,protocol_version,envelope,accepted_ms)
                    VALUES (?,?,?,?,1,?,?)
                    """, conversation, 810001, sender.deviceId(), UUID.randomUUID().toString(), bytes, System.currentTimeMillis());
        }
        var firstPage = json.readTree(mvc.perform(receiver.use(get(history)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(firstPage.size()).isEqualTo(50);
        long oldest = firstPage.get(49).get("sequence").asLong();
        var nextPage = json.readTree(mvc.perform(receiver.use(get(history).param("before", Long.toString(oldest))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(nextPage.size()).isEqualTo(5);
        assertThat(nextPage.get(0).get("sequence").asLong()).isLessThan(oldest);
        long firstSequence = nextPage.get(4).get("sequence").asLong();
        var catchup = json.readTree(mvc.perform(receiver.use(get(history).param("after", Long.toString(firstSequence))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(catchup.size()).isEqualTo(50);
        assertThat(catchup.get(0).get("sequence").asLong()).isEqualTo(nextPage.get(3).get("sequence").asLong());
        long catchupEnd = catchup.get(49).get("sequence").asLong();
        var remainder = json.readTree(mvc.perform(receiver.use(get(history).param("after", Long.toString(catchupEnd))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(remainder.size()).isEqualTo(4);

        jdbc.update("UPDATE qva_devices SET status='REVOKED' WHERE id=?", receiver.deviceId());
        mvc.perform(receiver.use(get(history))).andExpect(status().isForbidden());
    }
}
