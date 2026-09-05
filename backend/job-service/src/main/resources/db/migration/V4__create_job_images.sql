CREATE TABLE job_images (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    position INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_job_images_job
        FOREIGN KEY (job_id)
        REFERENCES jobs(id),

    CONSTRAINT uq_job_images_position
        UNIQUE (job_id, position)
);