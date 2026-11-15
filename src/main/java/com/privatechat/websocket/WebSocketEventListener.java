package com.privatechat.websocket;

import com.privatechat.model.User;
import com.privatechat.repository.UserRepository;
import com.privatechat.service.OnlinePresenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/**
 * Listens for WebSocket session lifecycle events to track user presence.
 */
@Component
public class WebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);

    private final OnlinePresenceService presenceService;
    private final UserRepository userRepository;

    public WebSocketEventListener(OnlinePresenceService presenceService, UserRepository userRepository) {
        this.presenceService = presenceService;
        this.userRepository = userRepository;
    }

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = headers.getUser();
        if (principal != null) {
            String username = principal.getName();
            String displayName = getDisplayName(username);
            presenceService.userConnected(username, displayName);
            log.info("WebSocket session opened: user={}", username);
        }
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = headers.getUser();
        if (principal != null) {
            String username = principal.getName();
            String displayName = getDisplayName(username);
            presenceService.userDisconnected(username, displayName);
            log.info("WebSocket session closed: user={}", username);
        }
    }

    private String getDisplayName(String username) {
        return userRepository.findByUsername(username)
                .map(User::getDisplayName)
                .orElse(username);
    }
}

