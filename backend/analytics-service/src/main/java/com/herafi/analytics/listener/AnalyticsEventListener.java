package com.herafi.analytics.listener;

import com.herafi.analytics.dto.AnalyticsEvent;
import com.herafi.analytics.service.ProjectionService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class AnalyticsEventListener {
    private final ObjectMapper mapper;
    private final ProjectionService projections;
    private final InvalidationPublisher notifications;

    public AnalyticsEventListener(ObjectMapper mapper, ProjectionService projections,
                                  InvalidationPublisher notifications) {
        this.mapper = mapper;
        this.projections = projections;
        this.notifications = notifications;
    }

    @KafkaListener(id = "analytics-events", idIsGroup = false, topics = "analytics.events")
    public void consume(String payload) {
        var event = mapper.readValue(payload, AnalyticsEvent.class);
        // The separate transactional service commits before returning to this listener.
        var change = projections.apply(event);
        change.ifPresent(notifications::send);
    }
}
