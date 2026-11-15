package com.privatechat.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Set;

/**
 * Inbound channel interceptor for WebSocket security.
 *
 * <p>Enforces authorization rules on STOMP frames:
 * <ul>
 *   <li>SUBSCRIBE to any topic (/topic/**) requires an authenticated session.</li>
 *   <li>SEND requires an authenticated session and must target approved destinations (/app/chat.send).</li>
 *   <li>Direct SEND to broker topics or unapproved destinations is rejected.</li>
 * </ul>
 */
@Component
public class WebSocketSecurityInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSecurityInterceptor.class);

    private static final Set<String> APPROVED_SEND_DESTINATIONS = Set.of(
            "/app/chat.send"
    );

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        Principal user = accessor.getUser();
        String destination = accessor.getDestination();

        if (StompCommand.SUBSCRIBE.equals(command)) {
            if (destination != null && (destination.startsWith("/topic/") || destination.startsWith("/app/"))) {
                if (user == null) {
                    log.warn("Unauthenticated WebSocket subscription attempt rejected for: {}", destination);
                    throw new AccessDeniedException("Authentication required to subscribe to " + destination);
                }
            }
        } else if (StompCommand.SEND.equals(command)) {
            if (user == null) {
                log.warn("Unauthenticated WebSocket SEND attempt rejected for: {}", destination);
                throw new AccessDeniedException("Authentication required to send messages");
            }

            if (destination == null || !APPROVED_SEND_DESTINATIONS.contains(destination)) {
                log.warn("Unauthorized WebSocket SEND destination '{}' attempted by user '{}'",
                        destination, user.getName());
                throw new AccessDeniedException("Unauthorized destination: " + destination);
            }
        }

        return message;
    }
}

