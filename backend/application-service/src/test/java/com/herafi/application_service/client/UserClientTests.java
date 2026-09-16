package com.herafi.application_service.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import com.herafi.application_service.exceptions.ApplicationException;

class UserClientTests {
    private final UUID userId = UUID.randomUUID();
    private final AtomicReference<String> receivedKey = new AtomicReference<>();
    private final AtomicReference<String> receivedPath = new AtomicReference<>();
    private HttpServer server;
    private UserClient client;
    private volatile String body;
    private volatile int status = 200;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            receivedKey.set(exchange.getRequestHeaders().getFirst("X-Internal-Api-Key"));
            receivedPath.set(exchange.getRequestURI().getPath());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        client = new UserClient("http://127.0.0.1:" + server.getAddress().getPort(), "internal-test-key");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void activeWorkerPassesAndInternalRequestUsesAuthenticatedIdentity() {
        body = "{\"userId\":\"" + userId + "\",\"eligible\":true}";
        client.requireActiveWorker(userId);
        assertEquals("internal-test-key", receivedKey.get());
        assertEquals("/internal/users/" + userId + "/worker-eligibility", receivedPath.get());
    }

    @Test
    void ineligibleUserGetsForbidden() {
        body = "{\"userId\":\"" + userId + "\",\"eligible\":false}";
        var error = assertThrows(ApplicationException.class, () -> client.requireActiveWorker(userId));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        assertEquals("WORKER_REQUIRED", error.getCode());
    }

    @Test
    void incompleteMalformedOrMismatchedResponsesFailClosed() {
        for (String response : new String[]{"{}", "null", "invalid-json",
                "{\"userId\":\"" + userId + "\"}",
                "{\"userId\":\"" + UUID.randomUUID() + "\",\"eligible\":true}"}) {
            body = response;
            assertUnavailable();
        }
    }

    @Test
    void upstreamErrorsAreUnavailableNotEligibilityDenials() {
        body = "{}";
        for (int responseStatus : new int[]{401, 404, 500, 503}) {
            status = responseStatus;
            assertUnavailable();
        }
    }

    @Test
    void connectionFailureFailsClosed() {
        server.stop(0);
        assertUnavailable();
    }

    private void assertUnavailable() {
        var error = assertThrows(ApplicationException.class, () -> client.requireActiveWorker(userId));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
        assertEquals("USER_SERVICE_UNAVAILABLE", error.getCode());
    }
}
