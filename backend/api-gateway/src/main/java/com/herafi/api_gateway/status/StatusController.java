package com.herafi.api_gateway.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Public status endpoint backing the status page.
 *
 * <p>Services are discovered from Eureka rather than hardcoded, so a service
 * added later appears here with no change to this class — and a service that
 * has crashed disappears from the registry, which is itself the signal.
 *
 * <p>Deliberately unauthenticated: a status page nobody can reach during an
 * outage is not a status page.
 */
@RestController
@RequestMapping("/status")
public class StatusController {

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(2);

    private final DiscoveryClient discoveryClient;
    private final RestClient restClient;

    public StatusController(DiscoveryClient discoveryClient, RestClient.Builder restClientBuilder) {
        this.discoveryClient = discoveryClient;

        // Short timeouts on purpose: a hung service must not hang the status
        // page. Failing fast and reporting DOWN is the useful behaviour.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(PROBE_TIMEOUT);
        requestFactory.setReadTimeout(PROBE_TIMEOUT);

        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
    }

    @GetMapping
    public StatusResponse status() {
        List<ServiceStatus> services = discoveryClient.getServices().stream()
                .sorted()
                .map(this::probe)
                .toList();

        boolean allUp = services.stream().allMatch(s -> "UP".equals(s.status()));

        return new StatusResponse(
                allUp ? "UP" : "DEGRADED",
                Instant.now(),
                services);
    }

    private ServiceStatus probe(String serviceId) {
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);

        if (instances.isEmpty()) {
            return new ServiceStatus(serviceId, "DOWN", 0, "not registered");
        }

        // Probe one instance. Each is behind the same health endpoint, so a
        // healthy sample plus the registered count is enough for a status page.
        ServiceInstance instance = instances.get(0);

        try {
            HealthPayload health = restClient.get()
                    .uri(instance.getUri().resolve("/actuator/health"))
                    .retrieve()
                    .body(HealthPayload.class);

            String reported = health == null ? "UNKNOWN" : health.status();

            return new ServiceStatus(serviceId, reported, instances.size(), null);
        } catch (Exception exception) {
            // A service registered in Eureka but not answering is the exact
            // case a status page exists to surface, so it is reported rather
            // than thrown.
            return new ServiceStatus(
                    serviceId,
                    "DOWN",
                    instances.size(),
                    exception.getClass().getSimpleName());
        }
    }

    public record StatusResponse(String status, Instant checkedAt, List<ServiceStatus> services) {
    }

    public record ServiceStatus(String name, String status, int instances, String detail) {
    }

    private record HealthPayload(String status) {
    }
}
