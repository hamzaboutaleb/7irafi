CREATE TABLE analytics_processed_events (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX analytics_jobs_owner_created_idx ON analytics_jobs (client_id, created_at DESC, job_id);
CREATE INDEX analytics_applications_owner_submitted_idx ON analytics_applications (applicant_id, submitted_at DESC, application_id);
