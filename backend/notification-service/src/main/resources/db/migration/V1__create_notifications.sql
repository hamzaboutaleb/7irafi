-- One row per thing a person should know about.
--
-- The event's source lives in another service, so nothing here is a foreign
-- key: a notification is a copy of what was true when it happened, not a live
-- view of it. That is also why the title and body are stored rather than
-- rebuilt on read.
CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    recipient_id UUID NOT NULL,

    -- What happened, e.g. APPLICATION_RECEIVED. Kept as text rather than an
    -- enum column so a new producer never needs a migration here.
    type VARCHAR(60) NOT NULL,

    title VARCHAR(150) NOT NULL,
    body VARCHAR(500) NOT NULL,

    -- Where clicking it should go, e.g. /conversations/<id>.
    target_url VARCHAR(300),

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    read_at TIMESTAMP WITH TIME ZONE
);

-- The two questions asked constantly: "my notifications, newest first" and
-- "how many unread?". Both start from the recipient.
CREATE INDEX idx_notifications_recipient_created
    ON notifications (recipient_id, created_at DESC, id DESC);

-- Plain rather than partial (no WHERE read_at IS NULL): the tests run these
-- migrations on H2, which has no partial indexes. Leading with recipient_id
-- still answers "how many unread for this person".
CREATE INDEX idx_notifications_recipient_unread
    ON notifications (recipient_id, read_at);
