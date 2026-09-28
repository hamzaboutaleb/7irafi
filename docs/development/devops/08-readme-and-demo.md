# T8 — README, Demo Script, and Evaluation Rehearsal

Back to [task board](README.md). Depends on T3–T7.

## T8.1 README: Monitoring section

- [ ] URLs: Grafana `http://localhost:3000`, Prometheus `http://127.0.0.1:9090`,
  Alertmanager `http://127.0.0.1:9093`.
- [ ] What each dashboard panel means.
- [ ] Table of alert rules with thresholds.
- [ ] How to trigger an alert.
- [ ] Screenshots: targets UP, dashboard under load, firing alert.

## T8.2 README: ELK section

- [ ] Architecture diagram (services → Logstash → Elasticsearch → Kibana).
- [ ] Log format and field list.
- [ ] Index naming and the retention/ILM table.
- [ ] Archiving decision.
- [ ] Why Docker JSON logs were chosen as the input.
- [ ] Example Kibana queries: `service: "job-service"`, `requestId: "<id>"`,
  `level: "ERROR"`.
- [ ] The WSL2 `vm.max_map_count` note.

## T8.3 `.env.example`

- [ ] Contains every new variable: `APP_ENVIRONMENT`, `MINIO_ROOT_*`,
  `METRICS_USERNAME`, `METRICS_PASSWORD`, `PROMETHEUS_RETENTION`,
  `GRAFANA_ADMIN_*`, `ELASTIC_PASSWORD`, `KIBANA_SYSTEM_PASSWORD`,
  `LOGSTASH_ES_*`, `LOG_RETENTION_DAYS`.

## T8.4 Demo script — `infra/demo/traffic.sh`

- [ ] Register a client and a professional, and log in (keep the JWTs in shell
  variables; never print them).
- [ ] Create jobs, submit applications, accept one.
- [ ] Loop N times to produce visible request rate and latency.
- [ ] Error mode: wrong-password logins (4xx, login failures), invalid job payloads,
  and a 5xx. Stopping a downstream service while calling
  application-service can produce the 5xx.
- [ ] Print the `X-Request-Id` of one full request chain, for the Kibana trace step.

## T8.5 Evaluation demo checklist

Monitoring:

1. [ ] `docker compose -f infra/docker/docker-compose.yml up -d --build` (one
   command).
2. [ ] Prometheus targets page: every service UP.
3. [ ] Grafana 7irafi dashboard.
4. [ ] Run `traffic.sh`: request rate and latency rise.
5. [ ] Run `traffic.sh --errors`: the 4xx/5xx panels and login failures rise.
6. [ ] `docker compose stop user-service`: ServiceDown fires, and the
   availability panel turns red. Start it again and the alert resolves.

Logs:

7. [ ] Kibana: the login, job creation, and application events are visible.
8. [ ] Filter `service: "auth-service"`.
9. [ ] Filter `level: "ERROR"`.
10. [ ] Search the printed `requestId`: the same request appears in the gateway,
    application, job, and user logs.
11. [ ] `docker compose stop elasticsearch`, generate traffic, and show Logstash
    retrying. `docker compose start elasticsearch`: the missed logs appear.
12. [ ] ILM: `_ilm/explain` shows the policy, and the test index is deleted
    (T7.4).

## T8.6 Dry run

- [ ] From a fresh clone: `cp .env.example .env`, fill it in,
  `docker compose down -v`, `up --build`. Everything comes up with no manual
  steps.
- [ ] Time the full demo (target under 10 minutes).
- [ ] Memory check: the full stack plus ELK needs about 6–8 GB. If needed, raise the
  WSL2 limit in `%UserProfile%\.wslconfig` (`memory=10GB`) and document it.

## Acceptance criteria

Someone other than you can follow the README and run the full demo without help.
