package com.herafi.application_service.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "applications.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxEventPublisher {

    private final OutboxEventDispatcher dispatcher;

    @Scheduled(fixedDelayString = "${applications.outbox.poll-interval-ms}")
    public void publishPending() {
        for (int published = 0; published < 100; published++) {
            if (!dispatcher.publishNext()) {
                return;
            }
        }
    }
}
