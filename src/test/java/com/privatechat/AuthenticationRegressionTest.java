package com.privatechat;

import com.privatechat.config.PasswordManagementRunner;
import com.privatechat.model.User;
import com.privatechat.repository.UserRepository;
import com.privatechat.service.UserDetailsServiceImpl;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:sqlite:./target/test-auth-regression.db",
    "spring.flyway.enabled=true",
    "spring.flyway.clean-disabled=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "logging.level.com.privatechat=DEBUG",
    "logging.level.org.springframework.security=DEBUG"
})
class AuthenticationRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DaoAuthenticationProvider authenticationProvider;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Autowired
    private PasswordManagementRunner passwordManagementRunner;

    @MockBean
    private SimpMessagingTemplate simpMessagingTemplate;

    @Test
    @DisplayName("PasswordEncoder is BCrypt cost-12 and hashes verify properly")
    void testPasswordEncoderConfiguration() {
        assertThat(passwordEncoder).isInstanceOf(BCryptPasswordEncoder.class);

        String rawPassword = "ValidTestPassword123!#";
        String encoded = passwordEncoder.encode(rawPassword);

        assertThat(encoded).isNotNull();
        assertThat(encoded).startsWith("$2a$12$");
        assertThat(encoded).hasSize(60);
        assertThat(passwordEncoder.matches(rawPassword, encoded)).isTrue();
        assertThat(passwordEncoder.matches("WrongPassword123!#", encoded)).isFalse();
    }

    @Test
    @DisplayName("Password policy enforces length and character classes")
    void testPasswordPolicyValidation() {
        // Valid
        assertThatCode(() -> PasswordManagementRunner.validatePasswordPolicy("StrongPass123!@#"))
                .doesNotThrowAnyException();

        // Too short (<12)
        assertThatThrownBy(() -> PasswordManagementRunner.validatePasswordPolicy("Short1!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 12 characters");

        // Missing uppercase
        assertThatThrownBy(() -> PasswordManagementRunner.validatePasswordPolicy("lowercase123!@#"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("uppercase");

        // Missing lowercase
        assertThatThrownBy(() -> PasswordManagementRunner.validatePasswordPolicy("UPPERCASE123!@#"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lowercase");

        // Missing number
        assertThatThrownBy(() -> PasswordManagementRunner.validatePasswordPolicy("NoNumbersHere!@#"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("numeric digit");

        // Missing special character
        assertThatThrownBy(() -> PasswordManagementRunner.validatePasswordPolicy("NoSpecialChars123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("special character");
    }

    @Test
    @DisplayName("PasswordManagementRunner only permits approved deployment accounts")
    void testApprovedUsernamesConstraint() {
        assertThat(PasswordManagementRunner.isApprovedUsername("jyoshna")).isTrue();
        assertThat(PasswordManagementRunner.isApprovedUsername("meghana")).isTrue();
        assertThat(PasswordManagementRunner.isApprovedUsername("yash")).isTrue();
        assertThat(PasswordManagementRunner.isApprovedUsername("yashuu")).isTrue();

        assertThat(PasswordManagementRunner.isApprovedUsername("alice")).isFalse();
        assertThat(PasswordManagementRunner.isApprovedUsername("bob")).isFalse();
        assertThat(PasswordManagementRunner.isApprovedUsername("admin")).isFalse();
        assertThat(PasswordManagementRunner.isApprovedUsername("root")).isFalse();

        assertThatThrownBy(() -> passwordManagementRunner.updatePassword("alice", "ValidPassword123!#"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unauthorized username");
    }

    @Test
    @DisplayName("PasswordManagementRunner updates password and flushes to database")
    void testPasswordUpdateAndPersistence() {
        String testPassword = "UpdatedPassword456!$";
        passwordManagementRunner.updatePassword("yash", testPassword);

        User updatedUser = userRepository.findByUsernameIgnoreCase("yash").orElseThrow();
        assertThat(passwordEncoder.matches(testPassword, updatedUser.getPasswordHash())).isTrue();
        assertThat(updatedUser.getPasswordHash()).startsWith("$2a$12$");

        UserDetails userDetails = userDetailsService.loadUserByUsername("yash");
        assertThat(userDetails).isNotNull();
        assertThat(userDetails.isEnabled()).isTrue();

        Authentication auth = authenticationProvider.authenticate(
                new UsernamePasswordAuthenticationToken("yash", testPassword)
        );
        assertThat(auth.isAuthenticated()).isTrue();
    }

    @Test
    @DisplayName("Uninitialized account login attempt is rejected")
    void testUninitializedAccountRejection() {
        // meghana still has placeholder in test db
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("meghana"))
                .isInstanceOf(DisabledException.class)
                .hasMessageContaining("not been initialized");
    }

    @Test
    @DisplayName("Unknown user login attempt is rejected")
    void testUnknownUserRejection() {
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("nonexistent_user"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    @DisplayName("HTTP Login with correct credentials returns 200 JSON with username")
    void testHttpLoginSuccess() throws Exception {
        String password = "HttpLoginPass123!#";
        passwordManagementRunner.updatePassword("jyoshna", password);

        mockMvc.perform(post("/api/auth/login")
                .param("username", "jyoshna")
                .param("password", password))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.username").value("jyoshna"));
    }

    @Test
    @DisplayName("HTTP Login with bad credentials returns 401 generic error")
    void testHttpLoginBadCredentials() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .param("username", "jyoshna")
                .param("password", "WrongPassword123!#"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Invalid username or password"));
    }

    @Test
    @DisplayName("HTTP Login with uninitialized account returns 401 generic error")
    void testHttpLoginUninitializedAccount() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .param("username", "yashuu")
                .param("password", "AnyPassword123!#"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Invalid username or password"));
    }

    @Test
    @DisplayName("Verify initialized and placeholder accounts in a disposable migrated database")
    void testMigratedDatabaseAccountAudit(@TempDir Path fixture) throws Exception {
        String url = "jdbc:sqlite:" + fixture.resolve("chat.db");
        Flyway.configure().dataSource(url, "", "").load().migrate();
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            try (var update = conn.prepareStatement("UPDATE users SET password_hash=? WHERE username=?")) {
                update.setString(1, passwordEncoder.encode("DisposableFixture123!"));
                update.setString(2, "jyoshna");
                assertThat(update.executeUpdate()).isEqualTo(1);
            }

            // Integrity check
            try (ResultSet rs = stmt.executeQuery("PRAGMA integrity_check;")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("ok");
            }

            // Check users
            try (ResultSet rs = stmt.executeQuery("SELECT username, password_hash, is_active FROM users ORDER BY id;")) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    String username = rs.getString("username");
                    String hash = rs.getString("password_hash");
                    int active = rs.getInt("is_active");

                    assertThat(active).isEqualTo(1);
                    assertThat(hash).isNotNull();
                    assertThat(hash).startsWith("$2a$12$");
                    assertThat(hash).hasSize(60);

                    if ("jyoshna".equals(username)) {
                        // jyoshna has a custom initialized password
                        assertThat(hash).isNotEqualTo(UserDetailsServiceImpl.UNINITIALIZED_DEFAULT_HASH);
                    } else {
                        // meghana, yash, yashuu have the placeholder hash awaiting Set-Password.bat
                        assertThat(hash).isEqualTo(UserDetailsServiceImpl.UNINITIALIZED_DEFAULT_HASH);
                    }
                }
                assertThat(count).isEqualTo(4);
            }
        }
    }
}

