package com.herafi.notification_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final String allowedOrigin;
    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    public WebSocketConfig(@Value("${notifications.cors.allowed-origin}") String allowedOrigin,
            StompAuthChannelInterceptor stompAuthChannelInterceptor) {
        this.allowedOrigin = allowedOrigin;
        this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigin);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // /queue is required for per-user delivery: /user/queue/notifications is
        // rewritten to /queue/notifications-user<session>, and the broker only delivers
        // destinations whose prefix it was told to handle.
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override 
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }

}
