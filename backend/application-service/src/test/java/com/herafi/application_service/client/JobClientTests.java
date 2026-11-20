package com.herafi.application_service.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.herafi.shared.observability.RequestIdInterceptor;
import com.herafi.application_service.exceptions.ApplicationException;
import com.sun.net.httpserver.HttpServer;

class JobClientTests {

    private HttpServer server;
    private JobClient client;
    private String response;
    private String receivedKey;
    private final UUID jobId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/jobs/", exchange -> {
            receivedKey = exchange.getRequestHeaders().getFirst("X-Internal-Api-Key");
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var body = exchange.getResponseBody()) {
                body.write(bytes);
            }
        });
        server.start();
        client = new JobClient("http://127.0.0.1:" + server.getAddress().getPort(),
                "test-internal-key", new RequestIdInterceptor());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void retainsSkillAndCityFromTheInternalJobSummary() {
        response = """
                {"id":"%s","clientId":"%s","status":"OPEN","assignedWorkerId":null,
                 "skill":"PLUMBING","city":"CASABLANCA"}
                """.formatted(jobId, clientId);

        var summary = client.getSummary(jobId);

        assertThat(summary.skill()).isEqualTo("PLUMBING");
        assertThat(summary.city()).isEqualTo("CASABLANCA");
        assertThat(receivedKey).isEqualTo("test-internal-key");
    }

    @Test
    void summariesWithoutRequiredAnalyticsMetadataAreUnavailable() {
        response = """
                {"id":"%s","clientId":"%s","status":"OPEN","assignedWorkerId":null}
                """.formatted(jobId, clientId);

        assertThatThrownBy(() -> client.getSummary(jobId)).isInstanceOfSatisfying(
                ApplicationException.class, error -> assertThat(error.getCode()).isEqualTo("JOB_SERVICE_UNAVAILABLE"));
    }

    @Test
    void assignmentResponsesAlsoIncludeTheJobMetadata() {
        UUID workerId = UUID.randomUUID();
        response = """
                {"id":"%s","clientId":"%s","status":"ASSIGNED","assignedWorkerId":"%s",
                 "skill":"PLUMBING","city":"CASABLANCA"}
                """.formatted(jobId, clientId, workerId);

        assertThat(client.assignWorker(jobId, workerId).skill()).isEqualTo("PLUMBING");
    }
}
