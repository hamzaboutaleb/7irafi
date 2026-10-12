#!/bin/sh
set -e

KIBANA_URL="http://kibana:5601"

echo "waiting for Kibana"
# Match the "available" level only, not "unavailable" which contains it.
until curl -s -u "elastic:${ELASTIC_PASSWORD}" "$KIBANA_URL/api/status" | grep -q '"level":"available"'; do
  sleep 5
done

echo "importing saved objects"
# --fail-with-body catches transport/HTTP errors; but _import answers 200 even
# when individual objects fail, so the response is checked for "success":true.
response=$(curl -sS --fail-with-body -u "elastic:${ELASTIC_PASSWORD}" \
  -X POST "$KIBANA_URL/api/saved_objects/_import?overwrite=true" \
  -H 'kbn-xsrf: true' \
  --form file=@/scripts/7irafi-saved-objects.ndjson)
echo "$response"

if ! echo "$response" | grep -q '"success":true'; then
  echo "saved-objects import failed"
  exit 1
fi

echo "kibana-setup complete"
