-- The per-job row also serializes application inserts with incoming lifecycle events.
CREATE TABLE job_lifecycle_state (
    job_id UUID PRIMARY KEY,
    job_version BIGINT NOT NULL DEFAULT -1 CHECK (job_version >= -1),
    status VARCHAR(20),
    assigned_worker_id UUID,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE processed_job_events (
    event_id UUID PRIMARY KEY,
    job_id UUID NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
