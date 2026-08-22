CREATE TABLE skills (
    code VARCHAR(40) PRIMARY KEY,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE cities (
    code VARCHAR(40) PRIMARY KEY,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE jobs (
    id UUID PRIMARY KEY,
    client_id UUID NOT NULL,
    title VARCHAR(120) NOT NULL,
    description TEXT NOT NULL,
    skill VARCHAR(40) NOT NULL REFERENCES skills(code),
    city VARCHAR(40) NOT NULL REFERENCES cities(code),
    address_line VARCHAR(200),
    budget NUMERIC(10, 2) NOT NULL CHECK (budget > 0),
    estimated_duration VARCHAR(20) NOT NULL,
    preferred_time VARCHAR(20) NOT NULL DEFAULT 'ANYTIME',
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    assigned_worker_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT estimated_duration_valid CHECK (
        estimated_duration IN ('HALF_DAY', 'FULL_DAY', 'MULTI_DAY')
    ),
    CONSTRAINT preferred_time_valid CHECK (
        preferred_time IN ('MORNING', 'AFTERNOON', 'EVENING', 'ANYTIME')
    ),
    CONSTRAINT status_valid CHECK (
        status IN ('OPEN', 'ASSIGNED', 'COMPLETED', 'CANCELLED')
    ),
    CONSTRAINT assigned_worker_matches_status CHECK (
        (status IN ('ASSIGNED', 'COMPLETED') AND assigned_worker_id IS NOT NULL)
        OR (status IN ('OPEN', 'CANCELLED') AND assigned_worker_id IS NULL)
    )
);

CREATE INDEX idx_jobs_search ON jobs (status, skill, city);
CREATE INDEX idx_jobs_client ON jobs (client_id);