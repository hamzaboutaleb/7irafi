# T7 — Log Retention (ILM)

Back to [task board](README.md). Depends on T6.

## T7.1 ILM policy and index template

Policy `7irafi-logs-policy`:

```json
PUT _ilm/policy/7irafi-logs-policy
{
  "policy": {
    "phases": {
      "hot":    { "actions": { "set_priority": { "priority": 100 } } },
      "warm":   { "min_age": "2d", "actions": { "forcemerge": { "max_num_segments": 1 } } },
      "delete": { "min_age": "7d", "actions": { "delete": {} } }
    }
  }
}
```

Index template, so every daily index gets the policy and correct mappings:

```json
PUT _index_template/7irafi-logs
{
  "index_patterns": ["7irafi-logs-*"],
  "template": {
    "settings": {
      "number_of_replicas": 0,
      "index.lifecycle.name": "7irafi-logs-policy"
    },
    "mappings": {
      "properties": {
        "@timestamp":  { "type": "date" },
        "service":     { "type": "keyword" },
        "environment": { "type": "keyword" },
        "level":       { "type": "keyword" },
        "requestId":   { "type": "keyword" },
        "http.path":   { "type": "keyword" },
        "http.status": { "type": "integer" },
        "duration_ms": { "type": "long" }
      }
    }
  }
}
```

- [ ] Put both files in `infra/docker/elasticsearch/` (`ilm-policy.json`,
  `index-template.json`).
- [ ] Apply them from the `es-setup` container (T6.1) before Logstash starts
  writing.
- [ ] `number_of_replicas: 0` because the cluster is single-node; otherwise every
  index is yellow.

Rotation: daily indices (`7irafi-logs-YYYY.MM.dd`) rotate by name, so ILM only has
to delete by age. Rollover by size is not needed at this volume. Document this
choice.

## T7.2 Retention periods

| Environment | Hot | Delete after | Why |
| --- | --- | --- | --- |
| Development | 0–2 days | 7 days | Limited laptop disk |
| Production | 0–7 days | 30 days | Incident investigation window |

These are the DevOps definitions the lead asked for. Record them in the README; if
the lead wants different numbers, only the variable changes.

- [ ] Make the delete age configurable with `LOG_RETENTION_DAYS` (Compose default
  `7`), which `es-setup` substitutes into the policy. Production sets `30`.
- [ ] Record both periods in the README.

## T7.3 Archiving decision

The lead asked to document whether logs are archived, not to build archiving.

Decision to document:

- [ ] Development: logs are **not archived**; indices are deleted after
  `LOG_RETENTION_DAYS`.
- [ ] Production (documented, not implemented): archive with Elasticsearch
  snapshots to S3-compatible storage (MinIO is already in the stack), using a daily
  snapshot lifecycle policy kept for 90 days. Implementing it needs the lead's
  approval.

## T7.4 Verify deletion

Waiting 7 days is not possible during evaluation.

- [ ] Temporarily set `indices.lifecycle.poll_interval=10s` and apply a test policy
  with `delete.min_age: 1m` to an index like `7irafi-logs-test`.
- [ ] Show `GET 7irafi-logs-*/_ilm/explain` before, and the index missing after.
- [ ] Record the commands in the demo checklist (T8.5).

## Acceptance criteria

`GET 7irafi-logs-*/_ilm/explain` shows every index managed by
`7irafi-logs-policy`, and a test index is deleted automatically during the demo.
