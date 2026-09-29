-- The avatar itself lives in object storage; the database keeps only the key.
-- Nullable on purpose: no avatar is a normal state, and the UI falls back to
-- the person's initials.
--
-- Two statements rather than one ALTER with two clauses: the tests run these
-- same migrations on H2, which does not accept the combined form.
ALTER TABLE user_profiles ADD COLUMN avatar_key VARCHAR(200);

ALTER TABLE user_profiles ADD COLUMN avatar_updated_at TIMESTAMP WITH TIME ZONE;
