-- Existing rows retain unknown metadata until a job lifecycle snapshot supplies it.
-- Historical APPLICATION_SUBMITTED events are deliberately not synthesized.
ALTER TABLE job_applications ADD COLUMN job_skill VARCHAR(40);
ALTER TABLE job_applications ADD COLUMN job_city VARCHAR(40);
ALTER TABLE job_applications ADD COLUMN analytics_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE job_applications ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE job_applications ADD CONSTRAINT ck_application_analytics_metadata CHECK (
    (job_skill IS NULL AND job_city IS NULL)
    OR (job_skill IS NOT NULL AND job_city IS NOT NULL
        AND LENGTH(TRIM(job_skill)) > 0 AND LENGTH(TRIM(job_city)) > 0)
);
ALTER TABLE job_applications ADD CONSTRAINT ck_application_analytics_status
    CHECK (analytics_status IN ('PENDING', 'ACCEPTED', 'REJECTED'));
ALTER TABLE job_applications ADD CONSTRAINT ck_application_version CHECK (version >= 0);
