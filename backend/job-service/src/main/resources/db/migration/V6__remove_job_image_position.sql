ALTER TABLE job_images
    DROP CONSTRAINT IF EXISTS uq_job_images_position;

ALTER TABLE job_images
    DROP COLUMN IF EXISTS position;

CREATE INDEX IF NOT EXISTS idx_job_images_job_created_at
    ON job_images (job_id, created_at);
