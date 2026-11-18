#!/bin/sh
set -eu

# Start Kafka using its original startup script
/etc/kafka/docker/run &
KAFKA_PID=$!

# Forward shutdown signals to Kafka
shutdown() {
    trap - TERM INT
    kill -TERM "$KAFKA_PID" 2>/dev/null || true
    wait "$KAFKA_PID" 2>/dev/null || true
}

trap 'shutdown; exit 143' TERM
trap 'shutdown; exit 130' INT

echo "Waiting for Kafka..."
until /opt/kafka/bin/kafka-broker-api-versions.sh \
    --bootstrap-server localhost:9092 >/dev/null 2>&1; do

    if ! kill -0 "$KAFKA_PID" 2>/dev/null; then
        echo "ERROR: Kafka stopped unexpectedly"
        wait "$KAFKA_PID" || true
        exit 1
    fi

    sleep 2
done

echo "Kafka is ready"
# Initialize topics
/bin/sh /opt/kafka/init-topics.sh

echo "Kafka topics initialized"

wait "$KAFKA_PID"