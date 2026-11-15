package com.privatechat.service;

import com.privatechat.model.User;
import com.privatechat.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Loads user details for Spring Security authentication.
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    public static final String UNINITIALIZED_DEFAULT_HASH = "$2a$12$SH9Nx9MTSmzHtFNjoPw6mOQvMAh9iVxuZw5x3v0kcaQFEzpFlyWDG";

    private static final Logger log = LoggerFactory.getLogger(UserDetailsServiceImpl.class);

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> {
                    log.warn("Authentication attempt for unknown user: {}", username);
                    return new UsernameNotFoundException("User not found: " + username);
                });

        if (!user.isActive()) {
            log.warn("Authentication attempt for inactive user: {}", username);
            throw new UsernameNotFoundException("User account is disabled: " + username);
        }

        // Block login if user still has the uninitialized/shared default placeholder password
        if (UNINITIALIZED_DEFAULT_HASH.equals(user.getPasswordHash())) {
            log.warn("Authentication rejected for user '{}': password has not been initialized via Set-Password.bat", user.getUsername());
            throw new DisabledException("Account '" + user.getUsername() + "' has not been initialized with a custom password. Run Set-Password.bat first.");
        }

        log.debug("User '{}' loaded successfully with active custom credentials", user.getUsername());

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .roles("USER")
                .build();
    }

    @Transactional
    public void recordLogin(String username) {
        userRepository.findByUsernameIgnoreCase(username).ifPresent(user -> {
            userRepository.updateLastLogin(user.getId(), Instant.now());
        });
    }
}

