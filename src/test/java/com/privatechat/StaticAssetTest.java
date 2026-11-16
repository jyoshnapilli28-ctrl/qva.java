package com.privatechat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:sqlite:./target/test-static.db",
    "spring.flyway.enabled=true"
})
class StaticAssetTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void signedOutInviteesCanOpenTheRegistrationRoute() throws Exception {
        mockMvc.perform(get("/app/register"))
               .andExpect(status().isOk())
               .andExpect(forwardedUrl("/app/index.html"));
    }

    @Test
    void testCssAssetServed() throws Exception {
        mockMvc.perform(get("/css/chat.css"))
               .andExpect(status().isOk())
               .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/css")))
               .andExpect(content().string(org.hamcrest.Matchers.containsString(".hidden")));
    }

    @Test
    void testJsAssetsServed() throws Exception {
        mockMvc.perform(get("/js/chat.js"))
               .andExpect(status().isOk())
               .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("javascript")));

        mockMvc.perform(get("/js/sockjs.min.js"))
               .andExpect(status().isOk())
               .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("javascript")));

        mockMvc.perform(get("/js/stomp.min.js"))
               .andExpect(status().isOk())
               .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("javascript")));
    }
}
