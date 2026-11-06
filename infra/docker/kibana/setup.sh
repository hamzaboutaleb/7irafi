#!/bin/sh
set -e

KIBANA_URL="http://kibana:5601"

echo "waiting for Kibana"
# Kibana answers /api/status with a non-200 until it is ready, then 200 with
# status.overall.level == "available". Check the overall level specifically so a
# single healthy component can't satisfy the wait while Kibana is unavailable
# overall, and require the 200 so a transport/HTTP error doesn't slip through.
until body=$(curl -s -w '\n%{http_code}' -u "elastic:${ELASTIC_PASSWORD}" "$KIBANA_URL/api/status") \
  && [ "$(printf '%s' "$body" | tail -n1)" = "200" ] \
  && printf '%s' "$body" | grep -q '"overall":{"level":"available"'; do
  sleep 5
done

echo "importing saved objects"
# --fail-with-body catches transport/HTTP errors; but _import answers 200 even
# when individual objects fail, so the body is checked for "success":true too.
# Transient failures (Kibana still settling) are retried a few times.
attempt=1
max_attempts=5
while true; do
  if response=$(curl -sS --fail-with-body -u "elastic:${ELASTIC_PASSWORD}" \
      -X POST "$KIBANA_URL/api/saved_objects/_import?overwrite=true" \
      -H 'kbn-xsrf: true' \
      --form file=@/scripts/7irafi-saved-objects.ndjson) \
      && echo "$response" | grep -q '"success":true'; then
    break
  fi

  if [ "$attempt" -ge "$max_attempts" ]; then
    echo "saved-objects import failed after ${attempt} attempts"
    echo "$response"
    exit 1
  fi
  echo "import attempt ${attempt} failed, retrying in 5s"
  attempt=$((attempt + 1))
  sleep 5
done
echo "$response"

echo "kibana-setup complete"
