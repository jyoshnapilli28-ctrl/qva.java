package com.privatechat;

import com.privatechat.service.UserDetailsServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:sqlite:./release/Server/data/chat.db",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "logging.level.com.privatechat=DEBUG",
    "logging.level.org.springframework.security=DEBUG"
})
class AuthAuditTest {

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Test
    void testLoadJyoshnaFromProductionDb() {
        UserDetails user = userDetailsService.loadUserByUsername("jyoshna");
        assertThat(user).isNotNull();
        assertThat(user.getUsername()).isEqualTo("jyoshna");
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.isAccountNonLocked()).isTrue();
        assertThat(user.getPassword()).startsWith("$2a$12$");
        assertThat(user.getPassword()).hasSize(60);
        System.out.println("TEST EVIDENCE: jyoshna successfully loaded from production DB, active and not placeholder!");
    }
}

