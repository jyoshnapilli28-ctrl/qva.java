package com.privatechat.service;

import com.privatechat.dto.PresenceEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which users are currently connected via WebSocket.
 * Thread-safe using ConcurrentHashMap.
 */
@Service
public class OnlinePresenceService {

    private static final Logger log = LoggerFactory.getLogger(OnlinePresenceService.class);

    private final Set<String> onlineUsernames = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final SimpMessagingTemplate messagingTemplate;

    public OnlinePresenceService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void userConnected(String username, String displayName) {
        onlineUsernames.add(username);
        log.info("User connected: {} (online count: {})", username, onlineUsernames.size());
        broadcastPresence(new PresenceEvent(PresenceEvent.EventType.CONNECTED, username, displayName));
    }

    public void userDisconnected(String username, String displayName) {
        onlineUsernames.remove(username);
        log.info("User disconnected: {} (online count: {})", username, onlineUsernames.size());
        broadcastPresence(new PresenceEvent(PresenceEvent.EventType.DISCONNECTED, username, displayName));
    }

    public boolean isOnline(String username) {
        return onlineUsernames.contains(username);
    }

    public Set<String> getOnlineUsernames() {
        return Collections.unmodifiableSet(onlineUsernames);
    }

    private void broadcastPresence(PresenceEvent event) {
        try {
            messagingTemplate.convertAndSend("/topic/presence", event);
        } catch (Exception e) {
            log.warn("Failed to broadcast presence event: {}", e.getMessage());
        }
    }
}

