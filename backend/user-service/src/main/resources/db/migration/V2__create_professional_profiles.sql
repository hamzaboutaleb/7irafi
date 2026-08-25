CREATE TABLE professional_profiles (
    user_id UUID PRIMARY KEY
          REFERENCES user_profiles(id)
          ON DELETE CASCADE,
    biography VARCHAR(2000) NOT NULL,
    onboarding_status VARCHAR(30) NOT NULL DEFAULT 'INCOMPLETE',
    CONSTRAINT chk_professional_profiles_onboarding_status
        CHECK (onboarding_status IN ('INCOMPLETE', 'ACTIVE', 'SUSPENDED'))
    
);
