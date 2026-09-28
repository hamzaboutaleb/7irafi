# T5 — Alerting

Back to [task board](README.md). Depends on T3.

## T5.1 `infra/docker/prometheus/alerts.yml`

```yaml
groups:
  - name: 7irafi
    rules:
      - alert: ServiceDown
        expr: up == 0
        for: 30s
        labels: { severity: critical }
        annotations:
          summary: "{{ $labels.job }} is down"

      - alert: HighErrorRate
        expr: |
          sum by (application) (rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
            / sum by (application) (rate(http_server_requests_seconds_count[5m])) > 0.05
        for: 2m
        labels: { severity: warning }

      - alert: HighLatency
        expr: |
          histogram_quantile(0.95,
            sum by (le, application) (rate(http_server_requests_seconds_bucket[5m]))) > 1
        for: 5m
        labels: { severity: warning }

      - alert: DatabasePoolExhausted
        expr: hikaricp_connections_pending > 0
          or hikaricp_connections_active / hikaricp_connections_max > 0.9
        for: 2m
        labels: { severity: critical }

      - alert: HighJvmMemory
        expr: |
          sum by (application) (jvm_memory_used_bytes{area="heap"})
            / sum by (application) (jvm_memory_max_bytes{area="heap"}) > 0.9
        for: 5m
        labels: { severity: warning }

      - alert: DiskPressure
        expr: |
          (node_filesystem_avail_bytes{fstype!~"tmpfs|overlay"}
            / node_filesystem_size_bytes{fstype!~"tmpfs|overlay"}) < 0.10
        for: 5m
        labels: { severity: critical }
```

- [ ] ServiceDown
- [ ] HighErrorRate
- [ ] HighLatency
- [ ] DatabasePoolExhausted
- [ ] HighJvmMemory
- [ ] DiskPressure
- [ ] Validate with `promtool check rules alerts.yml` (run it inside the Prometheus
  image).
- [ ] Use a short `for:` on ServiceDown so the live demo fires quickly.

## T5.2 Metrics for disk pressure

The JVM does not expose host disk usage.

- [ ] Add `prom/node-exporter` to Compose, with the host root mounted read-only.
  On WSL2 / Docker Desktop this reports the Docker VM's disk, which is what fills
  up.
- [ ] Add a `node-exporter` scrape job to `prometheus.yml`.

## T5.3 Alertmanager

Decision: Alertmanager (the lead allowed Alertmanager or Grafana alerts;
`alerts.yml` is in the lead's required file list, so the rules live in Prometheus).

- [ ] Add a pinned `prom/alertmanager` service with
  `infra/docker/alertmanager/alertmanager.yml`, UI on `127.0.0.1:9093`.
- [ ] In `prometheus.yml`, add `alerting.alertmanagers` → `alertmanager:9093`.
- [ ] Receiver: a default receiver with no external integration is enough for the
  evaluation, because the Alertmanager UI shows firing alerts. If the team wants
  notifications (email, Discord), the webhook URL comes from `.env`. Ask the lead.
- [ ] Group by `alertname, application` to avoid alert storms.

## T5.4 Rehearse

- [ ] `docker compose stop user-service` → ServiceDown turns Pending, then Firing
  within about 1 minute.
- [ ] Start it again → the alert resolves.
- [ ] Trigger 5xx errors (see T8.4) → HighErrorRate fires.
- [ ] Take screenshots for the README.

## Acceptance criteria

All six rules load without errors (Status → Rules), and at least ServiceDown can be
fired and resolved live.
