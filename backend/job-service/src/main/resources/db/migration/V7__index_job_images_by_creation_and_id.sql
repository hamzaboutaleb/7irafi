DROP INDEX IF EXISTS idx_job_images_job_created_at;

CREATE INDEX idx_job_images_job_created_at_id
    ON job_images (job_id, created_at, id);
