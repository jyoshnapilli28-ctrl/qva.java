package com.privatechat.config;

import com.privatechat.model.User;
import com.privatechat.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.Console;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

/**
 * Administrative runner for safely setting unique user passwords.
 *
 * <p>Triggered when launching with {@code --set-password=<username>}.
 * Prompts for the password interactively (masking input when console is available),
 * confirms password input, enforces strong policy (min 12 chars, upper, lower, digit, special),
 * encodes it via the configured {@link PasswordEncoder}, updates the SQLite database,
 * and terminates cleanly without logging or printing the password or hash.
 */
@Component
public class PasswordManagementRunner implements ApplicationRunner {

    public static final Set<String> APPROVED_USERNAMES = Set.of("jyoshna", "meghana", "yash", "yashuu");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ConfigurableApplicationContext applicationContext;
    private final TransactionTemplate transactionTemplate;

    public PasswordManagementRunner(UserRepository userRepository,
                                    PasswordEncoder passwordEncoder,
                                    ConfigurableApplicationContext applicationContext,
                                    PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.applicationContext = applicationContext;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Checks if a username is one of the four approved custom users.
     */
    public static boolean isApprovedUsername(String username) {
        if (username == null) return false;
        return APPROVED_USERNAMES.contains(username.trim().toLowerCase());
    }

    /**
     * Validates that a password satisfies the strict security policy:
     * - Minimum 12 characters
     * - At least one uppercase letter (A-Z)
     * - At least one lowercase letter (a-z)
     * - At least one numeric digit (0-9)
     * - At least one special character (!@#$%^&*...)
     *
     * @throws IllegalArgumentException if any policy requirement is not met
     */
    public static void validatePasswordPolicy(String password) {
        if (password == null || password.length() < 12) {
            throw new IllegalArgumentException("Password must be at least 12 characters long.");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new IllegalArgumentException("Password must contain at least one uppercase letter (A-Z).");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new IllegalArgumentException("Password must contain at least one lowercase letter (a-z).");
        }
        if (!password.matches(".*[0-9].*")) {
            throw new IllegalArgumentException("Password must contain at least one numeric digit (0-9).");
        }
        if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) {
            throw new IllegalArgumentException("Password must contain at least one special character (e.g. !@#$%^&*).");
        }
    }

    /**
     * Programmatic password update helper for services and test suites.
     */
    public void updatePassword(String username, String newPassword) {
        if (!isApprovedUsername(username)) {
            throw new IllegalArgumentException("Unauthorized username: '" + username + "'. Allowed: " + APPROVED_USERNAMES);
        }
        validatePasswordPolicy(newPassword);

        String canonical = username.trim().toLowerCase();
        transactionTemplate.execute(status -> {
            User user = userRepository.findByUsernameIgnoreCase(canonical)
                    .orElseThrow(() -> new IllegalArgumentException("User not found in database: " + canonical));
            user.setPasswordHash(passwordEncoder.encode(newPassword));
            userRepository.saveAndFlush(user);
            return null;
        });
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("set-password")) {
            return;
        }

        List<String> values = args.getOptionValues("set-password");
        if (values == null || values.isEmpty()) {
            System.err.println("Usage: --set-password=<username>");
            System.err.println("Approved usernames: jyoshna, meghana, yash, yashuu");
            exitApplication(1);
            return;
        }

        String username = values.get(0).trim().toLowerCase();
        if (!isApprovedUsername(username)) {
            System.err.println("ERROR: Unauthorized username '" + username + "'.");
            System.err.println("Only the four approved deployment accounts are permitted: jyoshna, meghana, yash, yashuu");
            exitApplication(1);
            return;
        }

        User user = userRepository.findByUsernameIgnoreCase(username).orElse(null);
        if (user == null) {
            System.err.println("ERROR: User '" + username + "' does not exist in the database.");
            exitApplication(1);
            return;
        }

        Console console = System.console();
        String newPassword;
        String confirmPassword;

        if (console != null) {
            char[] p1 = console.readPassword("Enter new password for user '%s': ", username);
            if (p1 == null || p1.length == 0) {
                System.err.println("ERROR: Password cannot be empty.");
                exitApplication(1);
                return;
            }
            char[] p2 = console.readPassword("Confirm new password for user '%s': ", username);
            if (p2 == null || p2.length == 0) {
                System.err.println("ERROR: Password confirmation cannot be empty.");
                exitApplication(1);
                return;
            }
            newPassword = new String(p1);
            confirmPassword = new String(p2);
        } else {
            Scanner scanner = new Scanner(System.in);
            System.out.print("Enter new password for user '" + username + "': ");
            if (!scanner.hasNextLine()) {
                System.err.println("ERROR: Password input missing.");
                exitApplication(1);
                return;
            }
            newPassword = scanner.nextLine().trim();

            System.out.print("Confirm new password for user '" + username + "': ");
            if (!scanner.hasNextLine()) {
                System.err.println("ERROR: Confirmation input missing.");
                exitApplication(1);
                return;
            }
            confirmPassword = scanner.nextLine().trim();
        }

        if (!newPassword.equals(confirmPassword)) {
            System.err.println("ERROR: Passwords do not match. Assignment aborted.");
            exitApplication(1);
            return;
        }

        try {
            validatePasswordPolicy(newPassword);
        } catch (IllegalArgumentException e) {
            System.err.println("ERROR: " + e.getMessage());
            System.err.println("Policy requirement: Minimum 12 characters, with uppercase, lowercase, number, and special character.");
            exitApplication(1);
            return;
        }

        String newHash = passwordEncoder.encode(newPassword);
        transactionTemplate.execute(status -> {
            User u = userRepository.findByUsernameIgnoreCase(username)
                    .orElseThrow(() -> new IllegalArgumentException("User not found in database: " + username));
            u.setPasswordHash(newHash);
            userRepository.saveAndFlush(u);
            return null;
        });

        System.out.println("SUCCESS: Password for user '" + username + "' updated successfully.");
        exitApplication(0);
    }

    private void exitApplication(int exitCode) {
        if (applicationContext != null && applicationContext.isActive()) {
            SpringApplication.exit(applicationContext, () -> exitCode);
        }
        System.exit(exitCode);
    }
}

