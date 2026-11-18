#!/bin/sh
set -eu

bootstrap_servers="${KAFKA_BOOTSTRAP_SERVERS:-kafka:9092}"
partitions="${KAFKA_TOPIC_PARTITIONS:-3}"

for topic in analytics.events jobs.lifecycle notification.request users.registered; do
    /opt/kafka/bin/kafka-topics.sh \
        --bootstrap-server "$bootstrap_servers" \
        --create --if-not-exists \
        --topic "$topic" \
        --partitions "$partitions" \
        --replication-factor 1

    /opt/kafka/bin/kafka-topics.sh \
        --bootstrap-server "$bootstrap_servers" \
        --describe --topic "$topic"
done
