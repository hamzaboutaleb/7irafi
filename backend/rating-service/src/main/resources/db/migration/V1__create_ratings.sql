-- One row per rating given by a rater to a ratee for a specific job.
-- Bidirectional: client can rate professional, professional can rate client.
-- Unique constraint prevents duplicate ratings for same job/rater/ratee combination.
CREATE TABLE ratings (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL,
    rater_id UUID NOT NULL,
    ratee_id UUID NOT NULL,
    score SMALLINT NOT NULL CHECK (score BETWEEN 1 AND 5),
    comment TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Prevent duplicate ratings for the same job/rater/ratee
CREATE UNIQUE INDEX uq_ratings_job_rater_ratee
    ON ratings (job_id, rater_id, ratee_id);

-- "All ratings received by this user, newest first"
CREATE INDEX idx_ratings_ratee_created
    ON ratings (ratee_id, created_at DESC, id DESC);

-- "All ratings given by this user, newest first"
CREATE INDEX idx_ratings_rater_created
    ON ratings (rater_id, created_at DESC, id DESC);

-- "All ratings for a specific job"
CREATE INDEX idx_ratings_job
    ON ratings (job_id);