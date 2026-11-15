package com.privatechat.config;

import com.privatechat.websocket.WebSocketSecurityInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket / STOMP broker configuration.
 *
 * <p>Topics:
 * <ul>
 *   <li>/topic/messages/{roomName} — new chat messages in a room</li>
 *   <li>/topic/presence            — user connect/disconnect events</li>
 * </ul>
 *
 * <p>Client sends messages to: /app/chat.send
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketSecurityInterceptor securityInterceptor;

    public WebSocketConfig(WebSocketSecurityInterceptor securityInterceptor) {
        this.securityInterceptor = securityInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // In-memory broker for topics (no external broker needed)
        config.enableSimpleBroker("/topic");
        // Prefix for client-to-server messages
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] allowedOrigins = {
            "http://localhost:*",
            "http://127.0.0.1:*",
            "http://100.*",
            "https://*.trycloudflare.com",
            "https://*.cloudflareaccess.com",
            "https://localhost:*",
            "https://127.0.0.1:*"
        };

        // SockJS fallback endpoint
        registry.addEndpoint("/ws/chat")
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS();

        // Direct native WebSocket endpoint
        registry.addEndpoint("/ws/chat")
                .setAllowedOriginPatterns(allowedOrigins);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(securityInterceptor);
    }
}
