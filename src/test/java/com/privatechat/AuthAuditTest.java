package com.privatechat;

import com.privatechat.config.PasswordManagementRunner;
import com.privatechat.service.UserDetailsServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AuthAuditTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws IOException {
        var database = Files.createTempDirectory("qva-auth-audit-").resolve("chat.db");
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + database);
    }

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Autowired
    private PasswordManagementRunner passwords;

    @Test
    void initializesAndLoadsTheSeedHostAccount() {
        passwords.updatePassword("jyoshna", "DisposableFixture123!");
        UserDetails user = userDetailsService.loadUserByUsername("jyoshna");
        assertThat(user).isNotNull();
        assertThat(user.getUsername()).isEqualTo("jyoshna");
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.isAccountNonLocked()).isTrue();
        assertThat(user.getPassword()).startsWith("$2a$12$");
        assertThat(user.getPassword()).hasSize(60);
    }
}

