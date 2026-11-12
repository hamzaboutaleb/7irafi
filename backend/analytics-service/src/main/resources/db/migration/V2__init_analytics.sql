CREATE TABLE analytics_jobs (
    job_id UUID PRIMARY KEY,
    client_id UUID NOT NULL,
    status VARCHAR(50),
    skill VARCHAR(100),
    city VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE analytics_applications (
    application_id UUID PRIMARY KEY,
    job_id UUID NOT NULL,
    applicant_id UUID NOT NULL,
    job_skill VARCHAR(100),
    job_city VARCHAR(100),
    status VARCHAR(50),
    submitted_at TIMESTAMP WITH TIME ZONE
);
