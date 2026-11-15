package com.privatechat;

import com.privatechat.websocket.WebSocketSecurityInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Automated security tests for WebSocket channel authorization.
 *
 * Verifies:
 * - Unauthenticated users cannot subscribe to message or presence topics.
 * - Unauthenticated users cannot send messages.
 * - Authenticated users can subscribe and send to approved destinations.
 * - Authenticated users cannot send to direct broker topics or unapproved destinations.
 */
class WebSocketSecurityTest {

    private WebSocketSecurityInterceptor interceptor;
    private MessageChannel channel;
    private Principal authenticatedUser;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketSecurityInterceptor();
        channel = mock(MessageChannel.class);
        authenticatedUser = () -> "jyoshna";
    }

    private Message<?> createStompMessage(StompCommand command, String destination, Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (user != null) {
            accessor.setUser(user);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    // --------------------------------------------------------
    // Unauthenticated subscription tests
    // --------------------------------------------------------

    @Test
    void testUnauthenticatedSubscribeToMessagesRejected() {
        Message<?> message = createStompMessage(StompCommand.SUBSCRIBE, "/topic/messages/general", null);
        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authentication required");
    }

    @Test
    void testUnauthenticatedSubscribeToPresenceRejected() {
        Message<?> message = createStompMessage(StompCommand.SUBSCRIBE, "/topic/presence", null);
        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authentication required");
    }

    // --------------------------------------------------------
    // Authenticated subscription tests
    // --------------------------------------------------------

    @Test
    void testAuthenticatedSubscribeToMessagesAllowed() {
        Message<?> message = createStompMessage(StompCommand.SUBSCRIBE, "/topic/messages/general", authenticatedUser);
        Message<?> result = interceptor.preSend(message, channel);
        assertThat(result).isNotNull();
    }

    @Test
    void testAuthenticatedSubscribeToPresenceAllowed() {
        Message<?> message = createStompMessage(StompCommand.SUBSCRIBE, "/topic/presence", authenticatedUser);
        Message<?> result = interceptor.preSend(message, channel);
        assertThat(result).isNotNull();
    }

    // --------------------------------------------------------
    // Unauthenticated send tests
    // --------------------------------------------------------

    @Test
    void testUnauthenticatedSendRejected() {
        Message<?> message = createStompMessage(StompCommand.SEND, "/app/chat.send", null);
        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authentication required to send messages");
    }

    // --------------------------------------------------------
    // Authenticated send tests
    // --------------------------------------------------------

    @Test
    void testAuthenticatedSendToApprovedDestinationAllowed() {
        Message<?> message = createStompMessage(StompCommand.SEND, "/app/chat.send", authenticatedUser);
        Message<?> result = interceptor.preSend(message, channel);
        assertThat(result).isNotNull();
    }

    @Test
    void testAuthenticatedSendToDirectTopicRejected() {
        Message<?> message = createStompMessage(StompCommand.SEND, "/topic/messages/general", authenticatedUser);
        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Unauthorized destination");
    }

    @Test
    void testAuthenticatedSendToUnapprovedAppDestinationRejected() {
        Message<?> message = createStompMessage(StompCommand.SEND, "/app/admin.shutdown", authenticatedUser);
        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Unauthorized destination");
    }
}

