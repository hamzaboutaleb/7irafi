package com.herafi.analytics.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.herafi.analytics.dto.AnalyticsEvent;
import com.herafi.analytics.service.ProjectionService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsEventListener {
    private final ObjectMapper mapper;
    private final ProjectionService projections;

    public AnalyticsEventListener(ObjectMapper mapper, ProjectionService projections) {
        this.mapper = mapper;
        this.projections = projections;
    }

    @KafkaListener(id = "analytics-events", idIsGroup = false, topics = "analytics.events")
    public void consume(String payload) throws JsonProcessingException {
        projections.apply(mapper.readValue(payload, AnalyticsEvent.class));
    }
}
