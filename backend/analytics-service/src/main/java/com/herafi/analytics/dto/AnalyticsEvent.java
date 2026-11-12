package com.herafi.analytics.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** Public Kafka contract. The existing eventType field selects the typed payload. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "eventType", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = JobEvent.class, names = {"JOB_CREATED", "JOB_STATUS_CHANGED", "JOB_DELETED"}),
        @JsonSubTypes.Type(value = ApplicationEvent.class,
                names = {"APPLICATION_SUBMITTED", "APPLICATION_ACCEPTED", "APPLICATION_REJECTED"})
})
public sealed interface AnalyticsEvent permits JobEvent, ApplicationEvent {
    UUID eventId();
    Instant timestamp();
}
