package com.herafi.analytics;

import java.time.Instant;
import java.util.UUID;

import com.herafi.analytics.config.AnalyticsStompInterceptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyticsSocketSecurityTest {
    private final JwtDecoder decoder = mock(JwtDecoder.class);
    private final AnalyticsStompInterceptor interceptor = new AnalyticsStompInterceptor(decoder);

    @Test
    void requiresABearerTokenOnConnect() {
        var connect = frame(StompCommand.CONNECT);
        assertThatThrownBy(() -> send(connect)).isInstanceOf(MessagingException.class)
                .hasMessage("Missing bearer token");
    }

    @Test
    void usesTheVerifiedTokenSubjectAsTheSessionUser() {
        String user = UUID.randomUUID().toString();
        when(decoder.decode("valid")).thenReturn(Jwt.withTokenValue("valid").header("alg", "HS256")
                .subject(user).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build());
        var connect = frame(StompCommand.CONNECT);
        connect.setNativeHeader("Authorization", "Bearer valid");

        send(connect);

        assertThat(connect.getUser().getName()).isEqualTo(user);
    }

    @Test
    void rejectsInvalidTokens() {
        when(decoder.decode("invalid")).thenThrow(new JwtException("Invalid signature"));
        var connect = frame(StompCommand.CONNECT);
        connect.setNativeHeader("Authorization", "Bearer invalid");

        assertThatThrownBy(() -> send(connect)).isInstanceOf(MessagingException.class)
                .hasMessage("Invalid bearer token");
    }

    @Test
    void allowsTheAuthenticatedUsersPrivateSubscription() {
        var subscribe = frame(StompCommand.SUBSCRIBE);
        subscribe.setUser(() -> UUID.randomUUID().toString());
        subscribe.setDestination("/user/queue/analytics");

        send(subscribe);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/queue/analytics", "/topic/analytics", "/user/someone-else/queue/analytics"})
    void rejectsOtherDestinations(String destination) {
        var subscribe = frame(StompCommand.SUBSCRIBE);
        subscribe.setUser(() -> UUID.randomUUID().toString());
        subscribe.setDestination(destination);

        assertThatThrownBy(() -> send(subscribe)).isInstanceOf(MessagingException.class)
                .hasMessage("Subscription denied");
    }

    @Test
    void rejectsSubscriptionsWithoutAUser() {
        var subscribe = frame(StompCommand.SUBSCRIBE);
        subscribe.setDestination("/user/queue/analytics");

        assertThatThrownBy(() -> send(subscribe)).isInstanceOf(MessagingException.class);
    }

    @Test
    void rejectsClientPublishedMessages() {
        var send = frame(StompCommand.SEND);
        send.setUser(() -> UUID.randomUUID().toString());
        send.setDestination("/queue/analytics");

        assertThatThrownBy(() -> send(send)).isInstanceOf(MessagingException.class)
                .hasMessage("Sending is not supported");
    }

    private StompHeaderAccessor frame(StompCommand command) {
        var accessor = StompHeaderAccessor.create(command);
        accessor.setLeaveMutable(true);
        return accessor;
    }

    private void send(StompHeaderAccessor accessor) {
        interceptor.preSend(MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()), null);
    }
}
