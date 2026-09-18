-- No foreign key: cleanup must survive rolled-back uploads and deleted image metadata.
CREATE TABLE job_image_cleanup (
    id UUID PRIMARY KEY,
    object_key VARCHAR(500) NOT NULL UNIQUE,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_job_image_cleanup_next_attempt
    ON job_image_cleanup (next_attempt_at, id);
