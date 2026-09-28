# T3 — Prometheus

Back to [task board](README.md). Depends on T1.

## T3.1 Compose service

```yaml
prometheus:
  image: prom/prometheus:<pinned-version>
  command:
    - --config.file=/etc/prometheus/prometheus.yml
    - --storage.tsdb.path=/prometheus
    - --storage.tsdb.retention.time=${PROMETHEUS_RETENTION:-15d}
    - --web.enable-lifecycle
  volumes:
    - ./prometheus:/etc/prometheus:ro
    - prometheus-data:/prometheus
  healthcheck:
    test: ["CMD-SHELL", "wget -qO- http://localhost:9090/-/healthy || exit 1"]
```

- [ ] Add the service and the `prometheus-data` volume.
- [ ] Publish the UI on the host loopback only: `"127.0.0.1:9090:9090"`. The lead's
  demo needs the targets page, and nothing outside the machine can reach it.
- [ ] Add `PROMETHEUS_RETENTION` to `.env.example`.

## T3.2 `infra/docker/prometheus/prometheus.yml`

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

rule_files:
  - /etc/prometheus/alerts.yml

scrape_configs:
  - job_name: auth-service
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['auth-service:8081']
  - job_name: user-service
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['user-service:8082']
  - job_name: job-service
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['job-service:8083']
  - job_name: application-service
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['application-service:8084']
  - job_name: api-gateway
    metrics_path: /actuator/prometheus
    basic_auth:                          # gateway port is public, see T1.2
      username: prometheus
      password_file: /etc/prometheus/secrets/metrics_password
    static_configs:
      - targets: ['api-gateway:8080']
  - job_name: discovery-service
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['discovery-service:8761']
  - job_name: prometheus
    static_configs:
      - targets: ['localhost:9090']
```

- [ ] Create the file (with `alerts.yml` as a placeholder until T5).
- [ ] Static targets, following the lead's example. When a service is added, add
  its job here (see the ongoing responsibilities on the task board).
- [ ] Gateway scrape password: Prometheus does not expand env variables in its
  config, so mount the password as a file. Use a Compose `secrets:` entry built
  from `METRICS_PASSWORD`, or a small entrypoint that writes it, and keep it equal
  to the gateway's `METRICS_PASSWORD`.

## T3.3 Verify

- [ ] Status → Targets shows all 7 jobs as **UP**.
- [ ] Query `up` returns 1 for each job.
- [ ] Query `rate(http_server_requests_seconds_count[1m])` returns data after some
  traffic.
- [ ] Take a screenshot of the targets page for the PR and README.

## Acceptance criteria

All targets are UP, and data persists across `docker compose restart prometheus`.
