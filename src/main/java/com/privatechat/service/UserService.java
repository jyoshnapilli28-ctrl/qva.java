package com.privatechat.service;

import com.privatechat.dto.UserDto;
import com.privatechat.model.User;
import com.privatechat.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages user information and online presence tracking.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final OnlinePresenceService presenceService;

    public UserService(UserRepository userRepository, OnlinePresenceService presenceService) {
        this.userRepository = userRepository;
        this.presenceService = presenceService;
    }

    @Transactional(readOnly = true)
    public List<UserDto> getAllActiveUsers() {
        Set<String> onlineUsers = presenceService.getOnlineUsernames();
        return userRepository.findByIsActiveTrue().stream()
                .map(u -> toDto(u, onlineUsers.contains(u.getUsername())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<UserDto> getUserByUsername(String username) {
        return userRepository.findByUsernameIgnoreCase(username)
                .map(u -> toDto(u, presenceService.isOnline(u.getUsername())));
    }

    private UserDto toDto(User u, boolean online) {
        return new UserDto(u.getId(), u.getUsername(), u.getDisplayName(), u.getLastLogin(), online);
    }
}

