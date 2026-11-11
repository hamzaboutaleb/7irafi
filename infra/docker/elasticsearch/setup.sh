#!/bin/sh
set -e

ES_URL="http://elasticsearch:9200"

# Every provisioning call goes through this: -s quiet, -S still print errors,
# --fail-with-body turns an HTTP >= 400 (e.g. a 401 from a wrong password, or a
# 400 bad request) into a non-zero exit while still showing the response body,
# so `set -e` aborts the container instead of printing "complete" on a failure.
es() {
  curl -sS --fail-with-body -u "elastic:${ELASTIC_PASSWORD}" "$@"
}

# Escape backslashes and double-quotes so a password with special characters
# still produces valid JSON when embedded in a request body below.
esc() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

echo "waiting for Elasticsearch"
until curl -s -u "elastic:${ELASTIC_PASSWORD}" "$ES_URL/_cluster/health" | grep -qE '"status":"(green|yellow)"'; do
  sleep 5
done

echo "setting kibana_system password"
es -X POST "$ES_URL/_security/user/kibana_system/_password" \
  -H 'Content-Type: application/json' \
  -d "{\"password\":\"$(esc "$KIBANA_SYSTEM_PASSWORD")\"}"
echo

echo "creating logstash_writer role"
es -X PUT "$ES_URL/_security/role/logstash_writer" \
  -H 'Content-Type: application/json' \
  -d '{
    "cluster": ["monitor"],
    "indices": [
      { "names": ["7irafi-logs-*"], "privileges": ["create_index", "write", "manage_ilm"] }
    ]
  }'
echo

echo "creating logstash_writer user"
es -X POST "$ES_URL/_security/user/logstash_writer" \
  -H 'Content-Type: application/json' \
  -d "{\"password\":\"$(esc "$LOGSTASH_ES_PASSWORD")\",\"roles\":[\"logstash_writer\"]}"
echo

echo "applying ILM policy"
sed "s/__RETENTION_DAYS__/${LOG_RETENTION_DAYS}/g" /scripts/ilm-policy.json > /tmp/ilm-policy.json
es -X PUT "$ES_URL/_ilm/policy/7irafi-logs-policy" \
  -H 'Content-Type: application/json' \
  -d @/tmp/ilm-policy.json
echo

echo "applying index template"
es -X PUT "$ES_URL/_index_template/7irafi-logs" \
  -H 'Content-Type: application/json' \
  -d @/scripts/index-template.json
echo

echo "es-setup complete"
