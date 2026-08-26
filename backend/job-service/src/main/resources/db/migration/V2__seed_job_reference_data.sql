INSERT INTO skills (code)
VALUES
    ('PLUMBING'),
    ('ELECTRICAL'),
    ('PAINTING'),
    ('CLEANING'),
    ('CARPENTRY'),
    ('MASONRY'),
    ('GARDENING'),
    ('APPLIANCE_REPAIR')
ON CONFLICT (code) DO NOTHING;

INSERT INTO cities (code)
VALUES
    ('CASABLANCA'),
    ('RABAT'),
    ('MARRAKESH'),
    ('TANGIER'),
    ('AGADIR'),
    ('FEZ'),
    ('MEKNES'),
    ('OUJDA'),
    ('KENITRA'),
    ('EL_JADIDA')
ON CONFLICT (code) DO NOTHING;
