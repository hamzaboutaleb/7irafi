CREATE TABLE service_cities (
    code VARCHAR(50) PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE professional_profile_cities (
    professional_user_id UUID NOT NULL
        REFERENCES professional_profiles(user_id)
        ON DELETE CASCADE,
    city_code VARCHAR(50) NOT NULL
        REFERENCES service_cities(code)
        ON DELETE RESTRICT,
    CONSTRAINT pk_professional_profile_cities
        PRIMARY KEY (professional_user_id, city_code)
);

INSERT INTO service_cities (code, display_name) VALUES
    ('CASABLANCA', 'Casablanca'),
    ('RABAT', 'Rabat'),
    ('MARRAKECH', 'Marrakech'),
    ('TANGIER', 'Tangier'),
    ('AGADIR', 'Agadir'),
    ('FES', 'Fes'),
    ('MEKNES', 'Meknes'),
    ('OUJDA', 'Oujda'),
    ('KENITRA', 'Kenitra'),
    ('EL_JADIDA', 'El Jadida'),
    ('TETOUAN', 'Tetouan'),
    ('SAFI', 'Safi');
