package com.herafi.analytics.config;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

public class AnalyticsStompInterceptor implements ChannelInterceptor {
    private final JwtDecoder decoder;

    public AnalyticsStompInterceptor(JwtDecoder decoder) {
        this.decoder = decoder;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null || headers.getCommand() == null) {
            return message; // Heartbeats do not carry a STOMP command.
        }

        switch (headers.getCommand()) {
            case CONNECT -> headers.setUser(authenticate(headers.getFirstNativeHeader("Authorization")));
            case SUBSCRIBE -> authorizeSubscription(headers);
            case SEND -> throw new MessagingException("Sending is not supported");
            default -> { } // Disconnect, unsubscribe and protocol control frames.
        }
        return message;
    }

    private Principal authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new MessagingException("Missing bearer token");
        }
        try {
            String subject = decoder.decode(authorization.substring(7).trim()).getSubject();
            if (subject == null) {
                throw new MessagingException("Missing token subject");
            }
            UUID.fromString(subject);
            return new UsernamePasswordAuthenticationToken(subject, null, List.of());
        } catch (JwtException | IllegalArgumentException exception) {
            throw new MessagingException("Invalid bearer token");
        }
    }

    private void authorizeSubscription(StompHeaderAccessor headers) {
        if (headers.getUser() == null || !"/user/queue/analytics".equals(headers.getDestination())) {
            throw new MessagingException("Subscription denied");
        }
    }
}
