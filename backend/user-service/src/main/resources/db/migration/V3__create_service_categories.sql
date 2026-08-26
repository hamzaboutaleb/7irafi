CREATE TABLE service_categories (
    code VARCHAR(50) PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE
);


CREATE TABLE professional_profile_categories (
    professional_user_id UUID NOT NULL
    REFERENCES professional_profiles(user_id)
    ON DELETE CASCADE,
    category_code VARCHAR(50) NOT NULL
    REFERENCES service_categories(code)
    ON DELETE RESTRICT,
    CONSTRAINT pk_professional_profile_categories
        PRIMARY KEY (professional_user_id, category_code)
);

INSERT INTO service_categories (code, display_name, description) VALUES
    ('PLUMBING', 'Plumbing', 'Installation, maintenance, and repair of water and drainage systems.'),
    ('ELECTRICAL', 'Electrical', 'Installation, maintenance, and repair of electrical systems.'),
    ('CARPENTRY', 'Carpentry', 'Construction and repair of wooden structures, fixtures, and furniture.'),
    ('PAINTING', 'Painting', 'Interior and exterior painting and surface preparation.'),
    ('MASONRY', 'Masonry', 'Construction and repair using brick, stone, concrete, and related materials.'),
    ('TILING_FLOORING', 'Tiling & Flooring', 'Installation and repair of tile, wood, laminate, and other flooring.'),
    ('ROOFING', 'Roofing', 'Roof installation, inspection, maintenance, and repair.'),
    ('HVAC', 'Heating & Air Conditioning', 'Installation, maintenance, and repair of heating and cooling systems.'),
    ('APPLIANCE_REPAIR', 'Appliance Repair', 'Diagnosis, maintenance, and repair of household appliances.'),
    ('LOCKSMITH', 'Locksmith', 'Installation, replacement, and repair of locks and access hardware.'),
    ('WELDING_METALWORK', 'Welding & Metalwork', 'Fabrication and repair of metal structures and components.'),
    ('CLEANING', 'Cleaning', 'Residential, commercial, and specialized cleaning services.'),
    ('GARDENING_LANDSCAPING', 'Gardening & Landscaping', 'Garden maintenance, planting, and landscape improvement.'),
    ('PEST_CONTROL', 'Pest Control', 'Inspection, prevention, and treatment of common pests.'),
    ('MOVING_DELIVERY', 'Moving & Delivery', 'Transportation, moving assistance, and local delivery services.'),
    ('GENERAL_HANDYMAN', 'General Handyman', 'General maintenance, assembly, installation, and minor repairs.');
