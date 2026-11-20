-- Long-lived tokens used only to get a new access token (POST /auth/refresh).
-- Only a SHA-256 hash is stored, like a password: a database leak does not
-- give working tokens. Each token works once; using it revokes it.
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE
);

-- logout and "token reuse detected" revoke every token of one user
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
