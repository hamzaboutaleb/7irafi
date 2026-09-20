package com.herafi.chat_service.config;

import java.security.Principal;
import java.util.List;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/**
 * Identifies the person behind a websocket.
 *
 * The socket bypasses the gateway, so nothing has checked a token by the time a
 * frame arrives here. A browser cannot send an Authorization header when opening
 * a websocket, so the client sends it as a STOMP header on its CONNECT frame
 * instead -- not in the URL, which would leak the token into logs and history.
 *
 * Checked once, at CONNECT: the user is stored on the session, and every later
 * frame on that connection carries it.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;

    public StompAuthChannelInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (!StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        accessor.setUser(authenticate(accessor.getFirstNativeHeader(AUTHORIZATION_HEADER)));

        return message;
    }

    private Principal authenticate(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new MessagingException("Missing bearer token on CONNECT");
        }

        Jwt token;
        try {
            token = jwtDecoder.decode(authorizationHeader.substring(BEARER_PREFIX.length()).trim());
        } catch (JwtException exception) {
            // Deliberately vague: the client learns the token was rejected, not why.
            throw new MessagingException("Invalid bearer token on CONNECT");
        }

        // The subject is the user id, exactly what the gateway puts in X-User-Id.
        return new UsernamePasswordAuthenticationToken(token.getSubject(), null, List.of());
    }
}
