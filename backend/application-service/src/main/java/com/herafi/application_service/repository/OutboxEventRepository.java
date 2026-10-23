package com.herafi.application_service.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.herafi.application_service.entity.OutboxEvent;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    // An older pending version blocks its successors even while locked or waiting for a retry.
    // The caller must keep the transaction open until Kafka acknowledges delivery.
    @Query(value = """
            SELECT event.* FROM outbox_events event
            WHERE event.published_at IS NULL
              AND event.next_attempt_at <= :now
              AND NOT EXISTS (
                  SELECT 1 FROM outbox_events older
                  WHERE older.application_id = event.application_id
                    AND older.destination = event.destination
                    AND older.published_at IS NULL
                    AND older.application_version < event.application_version
              )
            ORDER BY event.next_attempt_at, event.created_at, event.id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<OutboxEvent> lockNextPending(@Param("now") Instant now);
}
