package com.herafi.application_service.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Tells notification-service that something worth knowing happened.
 *
 * Deliberately best effort: a message that was stored and delivered must not
 * fail because the notification service is down. Timeouts are short for the
 * same reason -- this call sits on the path of a user action.
 */
@Component
public class NotificationClient {

    private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);

    private final RestClient client;

    public NotificationClient(
            @LoadBalanced RestClient.Builder builder,
            @Value("${service.security.internal-api-key}") String internalApiKey) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(2));

        this.client = builder
                .baseUrl("http://NOTIFICATION-SERVICE")
                .requestFactory(factory)
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .build();
    }

    public void notify(UUID recipientId, String type, String title, String body, String targetUrl) {
        try {
            client.post()
                    .uri("/internal/notifications")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "recipientId", recipientId,
                            "type", type,
                            "title", title,
                            "body", body,
                            "targetUrl", targetUrl))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            log.atError().addKeyValue("event", "internal_call_failed").setCause(exception)
                    .log("Could not create a {} notification for {}: {}", type, recipientId, exception.getMessage());
        }
    }
}
