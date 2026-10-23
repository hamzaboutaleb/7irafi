package com.herafi.application_service.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.herafi.application_service.service.JobLifecycleEventHandler;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "applications.lifecycle", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JobLifecycleListener {

    private final ObjectMapper mapper;
    private final JobLifecycleEventHandler handler;

    @KafkaListener(id = "job-lifecycle", topics = "jobs.lifecycle",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(String payload) {
        // The handler's database transaction commits before returning to the Kafka listener.
        handler.handle(mapper.readValue(payload, JobLifecycleEvent.class));
    }
}
