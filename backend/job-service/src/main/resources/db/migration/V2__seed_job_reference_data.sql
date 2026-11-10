INSERT INTO skills (code)
VALUES
    ('PLUMBING'),
    ('ELECTRICAL'),
    ('CARPENTRY'),
    ('PAINTING'),
    ('MASONRY'),
    ('TILING_FLOORING'),
    ('ROOFING'),
    ('HVAC'),
    ('APPLIANCE_REPAIR'),
    ('LOCKSMITH'),
    ('WELDING_METALWORK'),
    ('CLEANING'),
    ('GARDENING_LANDSCAPING'),
    ('PEST_CONTROL'),
    ('MOVING_DELIVERY'),
    ('GENERAL_HANDYMAN')
ON CONFLICT (code) DO NOTHING;

INSERT INTO cities (code)
VALUES
    ('CASABLANCA'),
    ('RABAT'),
    ('MARRAKECH'),
    ('TANGIER'),
    ('AGADIR'),
    ('FES'),
    ('MEKNES'),
    ('OUJDA'),
    ('KENITRA'),
    ('EL_JADIDA'),
    ('TETOUAN'),
    ('SAFI')
ON CONFLICT (code) DO NOTHING;