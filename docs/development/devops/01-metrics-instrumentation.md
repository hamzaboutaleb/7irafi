# T1 — Metrics Instrumentation in Every Service

Back to [task board](README.md).

Services: auth-service, user-service, job-service, application-service, api-gateway,
discovery-service. All already include `spring-boot-starter-actuator`, but only
`health` is exposed.

## T1.1 Add the Prometheus registry

In each `pom.xml`:

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
    <scope>runtime</scope>
</dependency>
```

- [ ] auth-service
- [ ] user-service
- [ ] job-service
- [ ] application-service
- [ ] api-gateway
- [ ] discovery-service

## T1.2 Expose `/actuator/prometheus` privately

In each `application.properties`:

```properties
management.endpoints.web.exposure.include=health,prometheus
```

Keep it private to the Docker network (lead requirement).

Constraint: do **not** move actuator to a separate `management.server.port`. The
status page (`StatusController`) probes each registered instance at
`<instance URI>/actuator/health` on its main port, so moving actuator would mark
every service DOWN.

- [ ] Gateway: `GatewayRoutesConfig` must not route `/actuator/**` to backend
  services. Check it.
- [ ] Gateway: port 8080 is public, so `/actuator/prometheus` on the gateway requires
  HTTP basic credentials `${METRICS_USERNAME:-prometheus}` /
  `${METRICS_PASSWORD:-7irafi-development-metrics}` in `SecurityConfig`. Prometheus
  sends them (`basic_auth` in T3.2). `/actuator/health` stays public for the
  status page.
- [ ] Services with Spring Security (auth, user, job, application) must permit
  `/actuator/prometheus` without a JWT or gateway key. Check each `SecurityConfig`.
- [ ] Host ports the team publishes for development (Eureka `8761`, job-service
  `8083`) expose actuator on every network interface. Bind them to `127.0.0.1`
  (`"127.0.0.1:8083:8083"`): developers keep local access, and the metrics are no
  longer reachable from other machines. Tell the job-service owner in the PR.
- [ ] auth, user, and application services publish no ports, so they are already
  private.

## T1.3 Common tags

```properties
management.metrics.tags.application=${spring.application.name}
management.metrics.tags.environment=${APP_ENVIRONMENT:dev}
```

- [ ] Add to all services.
- [ ] Add `APP_ENVIRONMENT: ${APP_ENVIRONMENT:-dev}` to each service in Compose,
  and to `.env.example`.
- [ ] `instance` is added by Prometheus from the scrape target, so don't set it in
  the application.

## T1.4 HTTP latency histograms

```properties
management.metrics.distribution.percentiles-histogram.http.server.requests=true
management.metrics.distribution.slo.http.server.requests=100ms,300ms,1s
```

- [ ] Add to all services.
- [ ] Check that `http_server_requests_seconds_bucket` appears in the output.
- [ ] Request count, status, and error rate come from the `status` and `outcome` tags
  on `http_server_requests_seconds_count`.

## T1.5 Verify JVM and database pool metrics

These come automatically. Confirm each is present:

- [ ] `jvm_memory_used_bytes`, `jvm_gc_pause_seconds`, `jvm_threads_live_threads`
- [ ] `process_cpu_usage`, `system_cpu_usage`
- [ ] `hikaricp_connections_active`, `hikaricp_connections_pending`,
  `hikaricp_connections_max` (services with a database)

## T1.6 🤝 Business counters

These are on the lead's list, so they are DevOps work, but they go inside business
code. Agree the exact placement with each service owner and keep the change to a
counter increment: no behaviour changes.

Add counters with `MeterRegistry` at the point where each event succeeds or fails:

| Metric | Service | Where |
| --- | --- | --- |
| `herafi_jobs_created_total` | job-service | after the job is saved |
| `herafi_job_image_upload_failures_total` | job-service | upload `catch` block |
| `herafi_applications_submitted_total` | application-service | after submission is saved |
| `herafi_applications_accepted_total` | application-service | after acceptance |
| `herafi_login_failures_total` | auth-service | bad credentials path |

Example:

```java
Counter.builder("herafi.jobs.created")
        .description("Jobs successfully created")
        .register(meterRegistry)
        .increment();
```

- [ ] Agree placement with each service owner.
- [ ] Never use user IDs or emails as tags. Unbounded tag values explode cardinality.

## T1.7 Tests

- [ ] For each service, a test that `/actuator/prometheus` returns 200 without
  authentication, following the `*Tests` naming in `AGENTS.md`.
- [ ] Gateway: a test that `/actuator/prometheus` returns 401 without credentials
  and 200 with them, and that `/actuator/health` stays public.
- [ ] Unit tests for the counters that increment after the business action.

## Acceptance criteria

From inside the network, for each service:

```bash
docker compose -f infra/docker/docker-compose.yml exec api-gateway \
  wget -qO- http://auth-service:8081/actuator/prometheus | head
```

returns metrics with `application` and `environment` labels. From the host,
`curl http://localhost:8080/actuator/prometheus` returns 401, while
`http://localhost:4200/status` still shows every service UP.
