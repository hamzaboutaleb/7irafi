CREATE TABLE analytics_jobs (
    job_id UUID PRIMARY KEY,
    client_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    skill VARCHAR(40) NOT NULL,
    city VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_analytics_jobs_filters ON analytics_jobs (client_id, status, skill, city, created_at);

CREATE TABLE analytics_applications (
    application_id UUID PRIMARY KEY,
    job_id UUID NOT NULL,
    applicant_id UUID NOT NULL,
    job_skill VARCHAR(40) NOT NULL,
    job_city VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_analytics_apps_filters ON analytics_applications (applicant_id, status, job_skill, job_city, submitted_at);
