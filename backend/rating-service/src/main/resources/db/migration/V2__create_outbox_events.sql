-- Events waiting to be sent to Kafka. A row is saved in the same transaction
-- as the change it describes, so the event is never lost and never sent for
-- a change that was rolled back.
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    topic VARCHAR(120) NOT NULL,
    message_key VARCHAR(120) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_error TEXT
);

-- The relay asks "which rows are not sent yet and due now?" every few seconds.
CREATE INDEX idx_outbox_events_pending
    ON outbox_events (published_at, next_attempt_at);
