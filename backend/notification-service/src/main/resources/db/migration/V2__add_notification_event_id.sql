-- Existing and HTTP-created notifications can have no event ID.
ALTER TABLE notifications
    ADD COLUMN event_id UUID;

ALTER TABLE notifications
    ADD CONSTRAINT uq_notifications_event_id UNIQUE (event_id);
