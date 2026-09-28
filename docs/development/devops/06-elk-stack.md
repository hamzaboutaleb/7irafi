# T6 — ELK Stack (Elasticsearch, Logstash, Kibana)

Back to [task board](README.md). Depends on T2 (JSON logs with `requestId`).

## Architecture

```text
services --stdout JSON--> Docker json-file logs --(file input)--> Logstash --> Elasticsearch <-- Kibana
                          /var/lib/docker/containers            persistent queue   7irafi-logs-YYYY.MM.DD
```

Decision: **Docker JSON logs** as the Logstash input. The lead allowed either
Docker logs or TCP/HTTP. Docker logs mean:

- services only write JSON to stdout (T2.4), with no appender or dependency in
  business code;
- `docker compose logs` keeps working for the whole team;
- no extra shipper container: Logstash reads the files itself, and remembers
  its position, so nothing is lost when Logstash restarts.

## T6.1 Elasticsearch

```yaml
elasticsearch:
  image: docker.elastic.co/elasticsearch/elasticsearch:<pinned-8.x>
  environment:
    discovery.type: single-node
    ES_JAVA_OPTS: -Xms512m -Xmx512m
    xpack.security.enabled: "true"
    ELASTIC_PASSWORD: ${ELASTIC_PASSWORD:-7irafi-development-elastic}
  volumes:
    - elasticsearch-data:/usr/share/elasticsearch/data
  mem_limit: 1g
  healthcheck:
    test: ["CMD-SHELL", "curl -s -u elastic:$${ELASTIC_PASSWORD} http://localhost:9200/_cluster/health | grep -qE 'green|yellow'"]
```

- [ ] Add the service and the `elasticsearch-data` volume.
- [ ] **No published port.** The lead requires Elasticsearch to be inaccessible
  from outside; Kibana and Logstash reach it on the Docker network.
- [ ] Memory limits suitable for development: 512 MB heap, 1 GB container limit.
- [ ] On WSL2, `vm.max_map_count` must be at least 262144
  (`sudo sysctl -w vm.max_map_count=262144`). Document it in the README.
- [ ] One-shot `es-setup` container (curl image) that, once Elasticsearch is
  healthy:
  - [ ] sets the `kibana_system` password;
  - [ ] creates a `logstash_writer` role (write/create_index on `7irafi-logs-*`)
    and user;
  - [ ] applies the ILM policy and index template (T7.1).
- [ ] Put the setup script and JSON files in `infra/docker/elasticsearch/`.

## T6.2 Logstash — `infra/docker/logstash/pipeline/logstash.conf`

```conf
input {
  file {
    path           => "/var/lib/docker/containers/*/*-json.log"
    start_position => "beginning"
    sincedb_path   => "/usr/share/logstash/data/sincedb"
    codec          => "json"          # Docker's wrapper: {"log": "...", "stream": "...", "time": "..."}
  }
}

filter {
  # The application's own JSON line is inside the "log" string.
  json {
    source         => "log"
    skip_on_invalid_json => true
    remove_field   => ["log"]
  }

  # Keep only 7irafi application logs (they carry "service"); drop Postgres,
  # MinIO, Logstash itself, and any other container on the machine.
  if ![service] { drop { } }

  if ![environment] { mutate { add_field => { "environment" => "${APP_ENVIRONMENT:dev}" } } }
  mutate { remove_field => ["stream", "time", "path", "host", "event"] }
}

output {
  elasticsearch {
    hosts    => ["http://elasticsearch:9200"]
    user     => "${LOGSTASH_ES_USER:logstash_writer}"
    password => "${LOGSTASH_ES_PASSWORD}"
    index    => "7irafi-logs-%{+YYYY.MM.dd}"
  }
}
```

- [ ] Create the pipeline file.
- [ ] Add a pinned Logstash service:
  - mount `./logstash/pipeline` read-only;
  - mount `/var/lib/docker/containers` read-only;
  - `LS_JAVA_OPTS=-Xms256m -Xmx256m`;
  - `logstash-data` volume (for sincedb and the queue);
  - no published port.
- [ ] Retry when Elasticsearch is unavailable: the Elasticsearch output retries
  indefinitely by default. Also turn on the persistent queue (`QUEUE_TYPE=persisted`,
  `QUEUE_MAX_BYTES=512mb`) so events are kept on disk during the outage.
- [ ] Daily index rotation: `7irafi-logs-%{+YYYY.MM.dd}` (uses the event's
  `@timestamp`, UTC).
- [ ] Check the path works on this machine: with Docker Engine inside WSL2 the logs
  are at `/var/lib/docker/containers`. If the team uses Docker Desktop, the same
  path resolves inside its VM; verify with
  `docker run --rm -v /var/lib/docker/containers:/c:ro alpine ls /c`.
- [ ] Make sure Compose's services use Docker's default `json-file` log driver (no
  `logging:` override), so the files exist.

## T6.3 Kibana

```yaml
kibana:
  image: docker.elastic.co/kibana/kibana:<same-version>
  environment:
    ELASTICSEARCH_HOSTS: http://elasticsearch:9200
    ELASTICSEARCH_USERNAME: kibana_system
    ELASTICSEARCH_PASSWORD: ${KIBANA_SYSTEM_PASSWORD:-7irafi-development-kibana}
  ports:
    - "127.0.0.1:5601:5601"
```

- [ ] Add the service, depending on `es-setup` completing successfully.
- [ ] Access is protected: users log in with `elastic` / `${ELASTIC_PASSWORD}`.
- [ ] Add `ELASTIC_PASSWORD`, `KIBANA_SYSTEM_PASSWORD`, `LOGSTASH_ES_USER`, and
  `LOGSTASH_ES_PASSWORD` to `.env.example`.

## T6.4 Data view and dashboards

- [ ] Data view `7irafi-logs-*`, time field `@timestamp`.
- [ ] Saved searches and visualisations:
  - [ ] Errors by service (`level: ERROR`, split by `service`)
  - [ ] Requests by endpoint (`http.path` terms)
  - [ ] Login failures (`event: login_failed`)
  - [ ] Job / application failures
  - [ ] Slow requests (`duration_ms > 1000`)
  - [ ] Internal API failures (`event: internal_call_failed`)
  - [ ] Logs by correlation ID (a saved search with a `requestId` filter control)
- [ ] One "7irafi Logs" dashboard combining them.

## T6.5 Export and auto-import

- [ ] Export the data view and dashboards as ndjson (Stack Management → Saved
  Objects) into `infra/docker/kibana/7irafi-saved-objects.ndjson`.
- [ ] A one-shot `kibana-setup` container that waits for Kibana and imports the
  file via `POST /api/saved_objects/_import?overwrite=true`.

## Acceptance criteria

- After a clean `up`, Kibana shows the `7irafi-logs-*` data view and dashboard
  without manual setup.
- Logs from all 6 services are searchable by `service`, `level`, and `requestId`,
  and no Postgres or MinIO lines are indexed.
- Stopping Elasticsearch loses no logs: they appear after it restarts.
