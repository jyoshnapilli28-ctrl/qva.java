package com.privatechat.websocket;

import com.privatechat.dto.InboundMessageDto;
import com.privatechat.dto.MessageDto;
import com.privatechat.exception.ChatException;
import com.privatechat.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Handles inbound STOMP messages from WebSocket clients.
 */
@Controller
public class ChatWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketController.class);

    private final MessageService messageService;

    public ChatWebSocketController(MessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * Handles a chat message sent by a client.
     * Client sends to: /app/chat.send
     * Server broadcasts to: /topic/messages/{roomName}
     */
    @MessageMapping("/chat.send")
    public void handleMessage(@Payload InboundMessageDto inbound,
                              SimpMessageHeaderAccessor headerAccessor,
                              Principal principal) {
        if (principal == null) {
            log.warn("Unauthenticated WebSocket message rejected");
            return;
        }

        String username = principal.getName();
        String roomName = inbound.getRoomName();
        if (roomName == null || roomName.isBlank()) {
            roomName = "general";
        }

        try {
            MessageDto saved = messageService.sendMessage(username, roomName, inbound.getContent());
            log.debug("WebSocket message handled: user={}, room={}, id={}", username, roomName, saved.getId());
        } catch (ChatException e) {
            log.warn("Message rejected for {}: {}", username, e.getMessage());
            // Error is not propagated back to avoid connection drops; client relies on feedback from REST
        }
    }
}

