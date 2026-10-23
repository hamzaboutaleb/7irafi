-- Event snapshots must survive independently of the job's later lifecycle.
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    destination VARCHAR(120) NOT NULL,
    job_id UUID NOT NULL,
    job_version BIGINT NOT NULL CHECK (job_version >= 0),
    payload JSONB NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL,
    last_error TEXT,
    CONSTRAINT uq_outbox_event_destination UNIQUE (event_id, destination)
);

CREATE INDEX idx_outbox_pending_delivery
    ON outbox_events (next_attempt_at, created_at, id)
    WHERE published_at IS NULL;

CREATE INDEX idx_outbox_pending_job_order
    ON outbox_events (job_id, destination, job_version)
    WHERE published_at IS NULL;
