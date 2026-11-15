package com.privatechat;

import com.privatechat.config.PasswordManagementRunner;
import com.privatechat.dto.MessageDto;
import com.privatechat.model.ChatRoom;
import com.privatechat.model.Message;
import com.privatechat.model.User;
import com.privatechat.repository.ChatRoomRepository;
import com.privatechat.repository.MessageRepository;
import com.privatechat.repository.UserRepository;
import com.privatechat.service.MessageService;
import com.privatechat.service.UserDetailsServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validates the four custom user accounts (jyoshna, meghana, yash, yashuu),
 * Flyway V3 migration safety, password policy enforcement, and authentication integrity.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:sqlite:./target/test-custom-accounts.db",
    "spring.flyway.enabled=true",
    "spring.flyway.clean-disabled=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "logging.level.com.privatechat=WARN",
    "logging.level.org.springframework.security=WARN"
})
class CustomAccountsMigrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private MessageService messageService;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Autowired
    private DaoAuthenticationProvider authenticationProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PasswordManagementRunner passwordManagementRunner;

    @MockBean
    private SimpMessagingTemplate simpMessagingTemplate;

    @Test
    @DisplayName("1. Exactly four approved accounts exist after V1-V3 migration")
    void testExactlyFourApprovedAccountsExist() {
        List<User> activeUsers = userRepository.findByIsActiveTrue();
        assertThat(activeUsers).hasSize(4);

        List<String> usernames = activeUsers.stream().map(User::getUsername).toList();
        assertThat(usernames).containsExactlyInAnyOrder("jyoshna", "meghana", "yash", "yashuu");
    }

    @Test
    @DisplayName("2. User IDs 1..4 and relationships are preserved")
    void testUserIdsPreserved() {
        User u1 = userRepository.findByUsername("jyoshna").orElseThrow();
        User u2 = userRepository.findByUsername("meghana").orElseThrow();
        User u3 = userRepository.findByUsername("yash").orElseThrow();
        User u4 = userRepository.findByUsername("yashuu").orElseThrow();

        assertThat(u1.getId()).isEqualTo(1L);
        assertThat(u2.getId()).isEqualTo(2L);
        assertThat(u3.getId()).isEqualTo(3L);
        assertThat(u4.getId()).isEqualTo(4L);

        assertThat(u1.getDisplayName()).isEqualTo("Jyoshna");
        assertThat(u2.getDisplayName()).isEqualTo("Meghana");
        assertThat(u3.getDisplayName()).isEqualTo("Yash");
        assertThat(u4.getDisplayName()).isEqualTo("Yashuu");
    }

    @Test
    @DisplayName("3. Old usernames cannot authenticate or exist")
    void testOldUsernamesDoNotExist() {
        for (String oldUser : List.of("alice", "bob", "carol", "dave")) {
            assertThat(userRepository.existsByUsername(oldUser)).isFalse();
            assertThat(userRepository.findByUsername(oldUser)).isEmpty();
            assertThatThrownBy(() -> userDetailsService.loadUserByUsername(oldUser))
                    .isInstanceOf(UsernameNotFoundException.class);
        }
    }

    @Test
    @DisplayName("4. Case-insensitive username lookup and unique enforcement")
    void testCaseInsensitiveUsernameMatching() {
        assertThat(userRepository.findByUsernameIgnoreCase("JYOSHNA")).isPresent();
        assertThat(userRepository.findByUsernameIgnoreCase("MeghAna")).isPresent();
        assertThat(userRepository.findByUsernameIgnoreCase("yAsh")).isPresent();
        assertThat(userRepository.findByUsernameIgnoreCase("YaShUu")).isPresent();

        assertThat(userRepository.existsByUsernameIgnoreCase("JyOsHnA")).isTrue();
        assertThat(userRepository.existsByUsernameIgnoreCase("ALICE")).isFalse();
    }

    @Test
    @DisplayName("5. Uninitialized accounts with default placeholder password are blocked from login")
    void testUninitializedDefaultPasswordIsBlocked() {
        // Reset password to placeholder to verify block
        User u = userRepository.findByUsername("yashuu").orElseThrow();
        u.setPasswordHash(UserDetailsServiceImpl.UNINITIALIZED_DEFAULT_HASH);
        userRepository.save(u);

        // Attempt authentication with old default password "chat1234"
        assertThatThrownBy(() -> authenticationProvider.authenticate(
                new UsernamePasswordAuthenticationToken("yashuu", "chat1234")))
                .isInstanceOf(org.springframework.security.core.AuthenticationException.class)
                .hasMessageContaining("not been initialized");
    }

    @Test
    @DisplayName("6. Initializing strong individual passwords allows authentication for all 4 users")
    void testAllFourUsersCanAuthenticateWithCustomPasswords() {
        String passJyoshna = "JyoshnaPass2026!#";
        String passMeghana = "MeghanaPass2026!#";
        String passYash = "YashPass2026!#";
        String passYashuu = "YashuuPass2026!#";

        passwordManagementRunner.updatePassword("jyoshna", passJyoshna);
        passwordManagementRunner.updatePassword("meghana", passMeghana);
        passwordManagementRunner.updatePassword("yash", passYash);
        passwordManagementRunner.updatePassword("yashuu", passYashuu);

        // Authenticate each user
        Authentication a1 = authenticationProvider.authenticate(
                new UsernamePasswordAuthenticationToken("jyoshna", passJyoshna));
        assertThat(a1.isAuthenticated()).isTrue();

        Authentication a2 = authenticationProvider.authenticate(
                new UsernamePasswordAuthenticationToken("meghana", passMeghana));
        assertThat(a2.isAuthenticated()).isTrue();

        Authentication a3 = authenticationProvider.authenticate(
                new UsernamePasswordAuthenticationToken("yash", passYash));
        assertThat(a3.isAuthenticated()).isTrue();

        Authentication a4 = authenticationProvider.authenticate(
                new UsernamePasswordAuthenticationToken("yashuu", passYashuu));
        assertThat(a4.isAuthenticated()).isTrue();

        // Also verify case-insensitive login
        Authentication a1Mixed = authenticationProvider.authenticate(
                new UsernamePasswordAuthenticationToken("JyOsHnA", passJyoshna));
        assertThat(a1Mixed.isAuthenticated()).isTrue();
        assertThat(a1Mixed.getName()).isEqualTo("jyoshna");
    }

    @Test
    @DisplayName("7. Each user's password hash is independently salted and unique")
    void testIndependentPasswordHashes() {
        String testPassword = "SharedPolicyPass123!#";
        passwordManagementRunner.updatePassword("jyoshna", testPassword);
        passwordManagementRunner.updatePassword("meghana", testPassword);

        User u1 = userRepository.findByUsername("jyoshna").orElseThrow();
        User u2 = userRepository.findByUsername("meghana").orElseThrow();

        assertThat(u1.getPasswordHash()).isNotEqualTo(u2.getPasswordHash());
        assertThat(passwordEncoder.matches(testPassword, u1.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(testPassword, u2.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("8. Incorrect passwords are systematically rejected")
    void testIncorrectPasswordRejected() {
        passwordManagementRunner.updatePassword("jyoshna", "CorrectPass123!#");

        assertThatThrownBy(() -> authenticationProvider.authenticate(
                new UsernamePasswordAuthenticationToken("jyoshna", "WrongPassword123!#")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("9. Password policy rejects weak or invalid passwords")
    void testPasswordPolicyEnforcement() {
        // Less than 12 chars
        assertThatThrownBy(() -> PasswordManagementRunner.validatePasswordPolicy("Short1!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 12 characters");

        // Missing uppercase
        assertThatThrownBy(() -> PasswordManagementRunner.validatePasswordPolicy("nouppercase123!@#"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("uppercase");

        // Missing lowercase
        assertThatThrownBy(() -> PasswordManagementRunner.validatePasswordPolicy("NOLOWERCASE123!@#"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lowercase");

        // Missing digits
        assertThatThrownBy(() -> PasswordManagementRunner.validatePasswordPolicy("NoDigitsHere!@#ABC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("digit");

        // Missing special characters
        assertThatThrownBy(() -> PasswordManagementRunner.validatePasswordPolicy("NoSpecial123456Abc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("special character");

        // Valid password
        PasswordManagementRunner.validatePasswordPolicy("StrongPass123!#$");
    }

    @Test
    @DisplayName("10. PasswordManagementRunner rejects unauthorized or unknown usernames")
    void testPasswordManagementRunnerRejectsUnauthorizedUsernames() {
        assertThatThrownBy(() -> passwordManagementRunner.updatePassword("alice", "StrongPass123!#$"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unauthorized username");

        assertThatThrownBy(() -> passwordManagementRunner.updatePassword("admin", "StrongPass123!#$"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unauthorized username");

        assertThat(PasswordManagementRunner.isApprovedUsername("jyoshna")).isTrue();
        assertThat(PasswordManagementRunner.isApprovedUsername("Meghana")).isTrue();
        assertThat(PasswordManagementRunner.isApprovedUsername("YASH")).isTrue();
        assertThat(PasswordManagementRunner.isApprovedUsername("yashuu")).isTrue();
        assertThat(PasswordManagementRunner.isApprovedUsername("alice")).isFalse();
    }

    @Test
    @DisplayName("11. Message history and sender associations survive under new usernames")
    void testMessageSenderAssociation() {
        String tag = "CustomAccountMsg-" + System.nanoTime();
        messageService.sendMessage("jyoshna", "general", tag);

        List<MessageDto> history = messageService.getHistory("general");
        MessageDto sent = history.stream()
                .filter(m -> m.getContent().equals(tag))
                .findFirst()
                .orElseThrow();

        assertThat(sent.getUsername()).isEqualTo("jyoshna");
        assertThat(sent.getDisplayName()).isEqualTo("Jyoshna");
    }
}

