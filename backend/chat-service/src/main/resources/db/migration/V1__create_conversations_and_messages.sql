-- One conversation per job application, and the messages inside it.
--
-- client_id, worker_id and job_id are copied from application-service when the
-- conversation is opened. Permission is checked once, at that moment, and then
-- lives here: a hire is a fact that is never undone, so there is nothing to
-- re-check on every message.
CREATE TABLE conversations (
    id UUID PRIMARY KEY,

    -- Unique on purpose: opening the same application twice must return the
    -- existing conversation rather than create a second one.
    application_id UUID NOT NULL UNIQUE,

    job_id UUID NOT NULL,
    client_id UUID NOT NULL,
    worker_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- "My conversations" is asked from both sides, and one person can be the client
-- on one job and the worker on another.
CREATE INDEX idx_conversations_client ON conversations (client_id);
CREATE INDEX idx_conversations_worker ON conversations (worker_id);

CREATE TABLE messages (
    id UUID PRIMARY KEY,

    conversation_id UUID NOT NULL
        REFERENCES conversations(id)
        ON DELETE CASCADE,

    -- No foreign key: users live in user-service's database, and services never
    -- reach across into each other's schema.
    sender_id UUID NOT NULL,

    body TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- History is always "this conversation, newest first". The id is part of the
-- index so two messages written in the same instant still have a stable order.
CREATE INDEX idx_messages_conversation_created
    ON messages (conversation_id, created_at DESC, id DESC);
