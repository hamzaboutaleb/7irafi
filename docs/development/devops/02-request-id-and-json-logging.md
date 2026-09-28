# T2 — Correlation IDs and Structured JSON Logging

Back to [task board](README.md).

Current state: the gateway keeps an incoming `X-Request-Id` (see
`GatewayIdentityFilterTests`), but nothing generates it, puts it in logs, or returns
it. All services log in Spring's default plain-text format.

## T2.1 Gateway request-ID filter

In `api-gateway`, add a servlet filter that runs first:

- [ ] If the request has a valid `X-Request-Id` (e.g. up to 64 characters,
  `[A-Za-z0-9-]`), keep it. Otherwise generate a UUID. Validation stops clients
  from injecting junk into logs.
- [ ] Put it in the MDC as `requestId`.
- [ ] Forward it downstream. `removeTrustedHeaders` must keep not removing it.
- [ ] Set it on the response header `X-Request-Id`.
- [ ] Clear the MDC in `finally`.
- [ ] Add `X-Request-Id` to the CORS exposed headers so the frontend can read it.

## T2.2 MDC filter in every service

For auth, user, job, application:

- [ ] A `OncePerRequestFilter` that reads `X-Request-Id` into the MDC (generating
  one if missing, e.g. for direct internal calls) and clears it afterwards.
- [ ] Echo it on the response.
- [ ] Include it in error response bodies if the service has a global exception
  handler.

## T2.3 Propagate the ID on internal calls

Internal REST clients that must forward `X-Request-Id` from the MDC:

- [ ] `auth-service/.../service/UserProfileClient.java` and
  `config/UserServiceClientConfig.java` (auth → user)
- [ ] `application-service/.../client/JobClient.java` (application → job)
- [ ] `application-service/.../client/UserClient.java` (application → user)

Use a `ClientHttpRequestInterceptor` or a `RestClient` default header
initializer registered where each client is configured, so every future call
inherits it and the client classes themselves don't change. These files belong
to the auth and application service owners: mention the change to them in the PR.

## T2.4 JSON log format

Decision: Spring Boot's built-in structured logging (all services are on Boot 4.1),
written to stdout. No new dependency and no `logback-spring.xml`. Logstash reads
these lines from Docker's log files (T6.2), so services need no log appender.

```properties
logging.structured.format.console=${LOG_FORMAT:logstash}
logging.structured.json.add.service=${spring.application.name}
logging.structured.json.add.environment=${APP_ENVIRONMENT:dev}
```

- [ ] Apply to all 6 services.
- [ ] Compose sets nothing extra: the default is JSON. Developers running
  `mvn spring-boot:run` outside Docker can set `LOG_FORMAT=` (empty) to get the
  usual plain-text logs, so their workflow is unchanged.
- [ ] Required fields: `@timestamp`, `service`, `environment`, `level`,
  `logger_name`, `message`, `requestId`, `stack_trace`.

## T2.5 Access-log filter

One log line per request, written after the response:

- [ ] Fields: `http.method`, `http.path`, `http.status`, `duration_ms`, `requestId`.
- [ ] Put it in a shared pattern in every service; the gateway line is the most
  important one.
- [ ] Don't log `/actuator/**` requests (Prometheus scrapes every few seconds).

## T2.6 Sensitive data

- [ ] Never log request bodies of `/auth/**`, `Authorization` headers,
  `X-Gateway-Key`, `X-Internal-Api-Key`, or uploaded file contents.
- [ ] Check that `SHOW_SQL=true` does not print bound parameter values.
- [ ] Grep the running logs for `Bearer `, `password`, and the dev secrets after the
  demo traffic, and record the result in the PR.

## T2.7 🤝 Business event logs

| Event | Service | Level |
| --- | --- | --- |
| Registration failure | auth-service | WARN |
| Login failure | auth-service | WARN |
| Job created | job-service | INFO |
| Image upload failure | job-service | ERROR |
| Application submitted | application-service | INFO |
| Application accepted | application-service | INFO |
| Internal service call failure | all clients | ERROR |

- [ ] Add a stable `event` field (e.g. `event=login_failed`) so Kibana can filter
  without parsing messages.
- [ ] Log IDs (job ID, application ID), never emails or passwords.

## T2.8 Tests and verification

- [ ] Gateway test: a generated ID appears on the response when none was sent.
- [ ] Gateway test: a valid incoming ID is kept; an invalid one is replaced.
- [ ] Service test: the MDC filter sets and clears `requestId`.
- [ ] Manual: send one request that calls application → job → user, then run
  `docker compose logs | grep <id>` and see the same ID in every service.

## Acceptance criteria

Every log line is one JSON object carrying `service` and `requestId`, and one request
can be followed across the gateway and every service it touches.
