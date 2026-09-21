CREATE TABLE job_applications (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL,
    applicant_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_job_applications_job_applicant UNIQUE (job_id, applicant_id)
);

CREATE INDEX idx_job_applications_applicant_created
    ON job_applications (applicant_id, created_at DESC);
CREATE INDEX idx_job_applications_job_created
    ON job_applications (job_id, created_at DESC);
