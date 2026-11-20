-- A directional "people I would vouch for" list.
--
-- One row per save: owner_id saved connection_id. The reverse is a separate row
-- and is not implied -- saving someone does not put you in their list.
--
-- Deliberately NOT enforced here: that the saved user is an active professional.
-- Professional status is mutable (a profile can be suspended after being saved),
-- so a database constraint would either block legitimate rows or go stale. That
-- rule lives in the service.
CREATE TABLE user_connections (
    owner_id UUID NOT NULL
        REFERENCES user_profiles(id)
        ON DELETE CASCADE,
    connection_id UUID NOT NULL
        REFERENCES user_profiles(id)
        ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    -- The pair, not either column alone: one owner may save many people, but
    -- never the same person twice.
    CONSTRAINT pk_user_connections
        PRIMARY KEY (owner_id, connection_id),

    -- Nobody appears in their own network.
    CONSTRAINT chk_user_connections_not_self
        CHECK (owner_id <> connection_id)
);

-- Reading a list is "everything owned by this user", so owner_id leads the
-- primary key and that index already serves it. This second index serves the
-- reverse question -- "who saved me?" -- which the profile page will want.
CREATE INDEX idx_user_connections_connection_id
    ON user_connections (connection_id);
