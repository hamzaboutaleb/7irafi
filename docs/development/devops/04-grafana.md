# T4 — Grafana

Back to [task board](README.md). Depends on T3.

## T4.1 Compose service

```yaml
grafana:
  image: grafana/grafana:<pinned-version>
  environment:
    GF_SECURITY_ADMIN_USER: ${GRAFANA_ADMIN_USER:-admin}
    GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:-7irafi-development-grafana}
    GF_USERS_ALLOW_SIGN_UP: "false"
  volumes:
    - grafana-data:/var/lib/grafana
    - ./grafana/provisioning:/etc/grafana/provisioning:ro
    - ./grafana/dashboards:/var/lib/grafana/dashboards:ro
  ports:
    - "3000:3000"
  depends_on:
    prometheus:
      condition: service_healthy
```

- [ ] Add the service and the `grafana-data` volume.
- [ ] Add `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD` to `.env.example`.
- [ ] Follow the team's secret pattern: a development default in Compose (like
  `JWT_SECRET`), overridden from `.env` in any real deployment. This meets the
  lead's "configured admin password, no hardcoded production password". Say so in
  the README.
- [ ] Healthcheck on `/api/health`.

## T4.2 `grafana/provisioning/datasources/prometheus.yml`

```yaml
apiVersion: 1
datasources:
  - name: Prometheus
    uid: prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: false
```

- [ ] Create the file. The fixed `uid` lets dashboards reference it reliably.

## T4.3 `grafana/provisioning/dashboards/dashboards.yml`

```yaml
apiVersion: 1
providers:
  - name: 7irafi
    folder: 7irafi
    type: file
    allowUiUpdates: false
    options:
      path: /var/lib/grafana/dashboards
```

- [ ] Create the file.

## T4.4 `grafana/dashboards/7irafi-overview.json`

Build it in the UI, export it as JSON ("Export for sharing externally" off, data
source uid `prometheus`), and commit it. Add a `service` template variable
(`label_values(up, job)`).

| Row / panel | Query (PromQL) |
| --- | --- |
| Request rate by service | `sum by (application) (rate(http_server_requests_seconds_count[1m]))` |
| 4xx errors | `sum by (application) (rate(http_server_requests_seconds_count{status=~"4.."}[1m]))` |
| 5xx errors | `sum by (application) (rate(http_server_requests_seconds_count{status=~"5.."}[1m]))` |
| Error ratio | 5xx rate / total rate per service |
| Latency p50/p95/p99 | `histogram_quantile(0.95, sum by (le, application) (rate(http_server_requests_seconds_bucket[5m])))` |
| JVM heap used | `sum by (application) (jvm_memory_used_bytes{area="heap"})` |
| CPU | `process_cpu_usage` |
| GC pauses | `rate(jvm_gc_pause_seconds_sum[1m])` |
| DB pool active / pending / max | `hikaricp_connections_active`, `hikaricp_connections_pending`, `hikaricp_connections_max` |
| Jobs created | `increase(herafi_jobs_created_total[1h])` |
| Applications submitted / accepted | `increase(herafi_applications_submitted_total[1h])`, `increase(herafi_applications_accepted_total[1h])` |
| Login failures, upload failures | `increase(herafi_login_failures_total[1h])`, `increase(herafi_job_image_upload_failures_total[1h])` |
| Service availability | `up` as a stat panel (green 1 / red 0) per job |

- [ ] Request rate panel
- [ ] 4xx / 5xx panels
- [ ] Latency percentiles panel
- [ ] JVM memory and CPU panels
- [ ] DB pool panel
- [ ] Business metrics row
- [ ] Availability row

## Acceptance criteria

After `docker compose down -v && up`, Grafana opens at `http://localhost:3000` with
the configured password, the Prometheus data source is already present, and the
7irafi dashboard shows live data without any manual clicks.
