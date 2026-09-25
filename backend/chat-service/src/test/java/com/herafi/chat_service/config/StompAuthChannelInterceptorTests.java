package com.herafi.chat_service.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class StompAuthChannelInterceptorTests {

    private static final String USER_ID = "8b6a1f5c-0f3a-4a54-9a7a-6a6a1f5c0f3a";

    private JwtDecoder jwtDecoder;
    private StompAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtDecoder = mock(JwtDecoder.class);
        interceptor = new StompAuthChannelInterceptor(jwtDecoder);
    }

    @Test
    void connectWithAValidTokenIdentifiesTheSession() {
        when(jwtDecoder.decode("good-token")).thenReturn(jwt());

        Message<?> message = connectFrame("Bearer good-token");
        interceptor.preSend(message, null);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        assertNotNull(accessor.getUser());
        assertEquals(USER_ID, accessor.getUser().getName());
    }

    @Test
    void connectWithoutATokenIsRefused() {
        assertThrows(MessagingException.class, () -> interceptor.preSend(connectFrame(null), null));
    }

    @Test
    void connectWithoutTheBearerPrefixIsRefused() {
        assertThrows(MessagingException.class, () -> interceptor.preSend(connectFrame("good-token"), null));
    }

    @Test
    void connectWithAnInvalidTokenIsRefused() {
        when(jwtDecoder.decode(anyString())).thenThrow(new JwtException("expired"));

        assertThrows(MessagingException.class, () -> interceptor.preSend(connectFrame("Bearer bad-token"), null));
    }

    @Test
    void otherFramesPassThroughUntouched() {
        Message<?> message = frame(StompCommand.SEND, null);

        assertEquals(message, interceptor.preSend(message, null));
        assertNull(StompHeaderAccessor.wrap(message).getUser());
    }

    private Message<?> connectFrame(String authorization) {
        return frame(StompCommand.CONNECT, authorization);
    }

    private Message<?> frame(StompCommand command, String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setLeaveMutable(true);
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }

        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Jwt jwt() {
        return new Jwt("good-token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "HS256"), Map.of("sub", USER_ID));
    }
}
