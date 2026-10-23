package com.herafi.chat_service.client;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.herafi.chat_service.exception.ChatException;

/**
 * Asks application-service who is involved in a job application.
 *
 * Called once, when a conversation is opened. After that the participants live
 * in chat_db and this service is never consulted about them again.
 */
@Component
public class ApplicationClient {

    private static final Logger log = LoggerFactory.getLogger(ApplicationClient.class);

    private final RestClient client;

    public ApplicationClient(
            RestClient applicationServiceRestClient,
            @Value("${service.security.internal-api-key}") String internalApiKey) {
        this.client = applicationServiceRestClient.mutate()
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .build();
    }

    public ChatContext getChatContext(UUID applicationId) {
        ChatContext context;

        try {
            context = client.get()
                    .uri("/internal/applications/{applicationId}/chat-context", applicationId)
                    .retrieve()
                    .body(ChatContext.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new ChatException(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND",
                        "Application not found");
            }

            throw unavailable(exception);
        } catch (RestClientException exception) {
            // Timeout, connection refused, no instance registered in Eureka.
            throw unavailable(exception);
        }

        // A mangled answer must not become a conversation: better to fail than to
        // store the wrong pair of people.
        if (context == null
                || !applicationId.equals(context.applicationId())
                || context.jobId() == null
                || context.applicantId() == null
                || context.clientId() == null) {
            throw unavailable();
        }

        return context;
    }

    private ChatException unavailable() {
        return unavailable(null);
    }

    private ChatException unavailable(RestClientException cause) {
        log.atError().addKeyValue("event", "internal_call_failed").setCause(cause)
                .log("Application service call failed");
        return new ChatException(HttpStatus.SERVICE_UNAVAILABLE, "APPLICATION_SERVICE_UNAVAILABLE",
                "Unable to reach the application service. Please retry.");
    }

    /**
     * Our own copy of the contract, deliberately not imported from
     * application-service: this service decides what it depends on.
     */
    public record ChatContext(
            UUID applicationId,
            UUID jobId,
            UUID applicantId,
            UUID clientId,
            String status) {
    }
}
