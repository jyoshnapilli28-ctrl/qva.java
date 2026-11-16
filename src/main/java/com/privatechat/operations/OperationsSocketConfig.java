package com.privatechat.operations;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Configuration
@EnableWebSocket
class OperationsSocketConfig implements WebSocketConfigurer {
    private static final String BINDING = "workspaceBinding";
    private final OperationsAccess access;
    private final WorkspaceSessions sessions;
    private final LiveConnections live;

    OperationsSocketConfig(OperationsAccess access, WorkspaceSessions sessions, LiveConnections live) {
        this.access = access;
        this.sessions = sessions;
        this.live = live;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new TextWebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                live.attach(session, (LiveConnections.Binding) session.getAttributes().get(BINDING));
            }

            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                live.heartbeat(session, message.getPayload());
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
                live.detach(session.getId(), null);
            }
        }, "/api/v2/live").addInterceptors(new HandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                           WebSocketHandler handler, Map<String, Object> attributes) {
                if (!(request instanceof ServletServerHttpRequest servlet) || request.getPrincipal() == null
                        || request.getHeaders().getOrigin() == null) {
                    response.setStatusCode(HttpStatus.FORBIDDEN);
                    return false;
                }
                var http = servlet.getServletRequest();
                try {
                    var actor = access.actor(request.getPrincipal().getName());
                    var device = access.currentDevice(actor, http);
                    var session = http.getSession(false);
                    if (device != null && session != null) {
                        var binding = new LiveConnections.Binding(actor.userId(), device.id(), session.getId());
                        if (live.valid(binding)) {
                            sessions.observe(session, actor, device);
                            attributes.put(BINDING, binding);
                            return true;
                        }
                    }
                } catch (OperationsException denied) {
                    // A rejected upgrade does not disclose ownership, account or approval details.
                }
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }

            @Override
            public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler handler, Exception failure) {}
        });
    }
}
