-- ==============================================================================
-- NEXUSMAP Database Migration V2: Enable PostGIS & Geofencing Zones
-- ==============================================================================

-- Enable PostGIS spatial extension (requires superuser or appropriate DB privileges)
CREATE EXTENSION IF NOT EXISTS postgis;

-- 1. Campus Zones Table
CREATE TABLE IF NOT EXISTS zones (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    zone_type VARCHAR(30) NOT NULL DEFAULT 'ACADEMIC',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    boundary GEOMETRY(Polygon, 4326) NOT NULL,
    center_lat DOUBLE PRECISION NOT NULL,
    center_lng DOUBLE PRECISION NOT NULL,
    color VARCHAR(20) DEFAULT '#00f0ff',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- GiST Spatial Index on Zone Boundaries
CREATE INDEX IF NOT EXISTS idx_zones_boundary_gist ON zones USING GIST (boundary);
CREATE INDEX IF NOT EXISTS idx_zones_active ON zones (active);

-- 2. User Locations Table
CREATE TABLE IF NOT EXISTS user_locations (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    location GEOMETRY(Point, 4326) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    accuracy DOUBLE PRECISION,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- GiST Spatial Index on User Locations
CREATE INDEX IF NOT EXISTS idx_user_locations_location_gist ON user_locations USING GIST (location);
CREATE INDEX IF NOT EXISTS idx_user_locations_user_id ON user_locations (user_id);
CREATE INDEX IF NOT EXISTS idx_user_locations_recorded_at ON user_locations (recorded_at);

-- 3. Zone Memberships Table
CREATE TABLE IF NOT EXISTS zone_memberships (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    zone_id BIGINT NOT NULL REFERENCES zones(id) ON DELETE CASCADE,
    entered_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    exited_at TIMESTAMP WITH TIME ZONE,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_zone_memberships_active ON zone_memberships (active);
CREATE INDEX IF NOT EXISTS idx_zone_memberships_user_id ON zone_memberships (user_id);
CREATE INDEX IF NOT EXISTS idx_zone_memberships_zone_id ON zone_memberships (zone_id);

-- 4. Sample Prototype Campus Zones
-- Reference: Prototype educational campus test boundaries
INSERT INTO zones (name, code, description, zone_type, active, center_lat, center_lng, color, boundary)
VALUES
(
    'Campus Perimeter Boundary',
    'ZONE-PERIMETER',
    'Overall designated perimeter of the prototype academic campus grounds.',
    'CAMPUS_PERIMETER',
    TRUE,
    37.7750,
    -122.4190,
    '#38bdf8',
    ST_SetSRID(ST_GeomFromText('POLYGON((-122.4300 37.7680, -122.4080 37.7680, -122.4080 37.7840, -122.4300 37.7840, -122.4300 37.7680))'), 4326)
),
(
    'Main Gate & Welcome Transit Plaza',
    'ZONE-MAIN-GATE',
    'Primary campus entrance, transit hub, and shuttle pickup area.',
    'TRANSIT',
    TRUE,
    37.7710,
    -122.4245,
    '#22c55e',
    ST_SetSRID(ST_GeomFromText('POLYGON((-122.4265 37.7695, -122.4225 37.7695, -122.4225 37.7725, -122.4265 37.7725, -122.4265 37.7695))'), 4326)
),
(
    'Central Academic Library',
    'ZONE-LIBRARY',
    'University archive collections, quiet study rooms, and research commons.',
    'ACADEMIC',
    TRUE,
    37.7745,
    -122.4200,
    '#eab308',
    ST_SetSRID(ST_GeomFromText('POLYGON((-122.4220 37.7730, -122.4180 37.7730, -122.4180 37.7760, -122.4220 37.7760, -122.4220 37.7730))'), 4326)
),
(
    'Student Canteen & Dining Commons',
    'ZONE-CANTEEN',
    'Food hall, dining commons, and student social terrace.',
    'DINING',
    TRUE,
    37.7785,
    -122.4200,
    '#f97316',
    ST_SetSRID(ST_GeomFromText('POLYGON((-122.4220 37.7770, -122.4180 37.7770, -122.4180 37.7800, -122.4220 37.7800, -122.4220 37.7770))'), 4326)
),
(
    'Innovation Lab & Tech Maker Studio',
    'ZONE-INNOVATION-LAB',
    'Advanced hardware prototyping lab, AI incubation lab, and collaborative space.',
    'ACADEMIC',
    TRUE,
    37.7775,
    -122.4145,
    '#00f0ff',
    ST_SetSRID(ST_GeomFromText('POLYGON((-122.4165 37.7760, -122.4125 37.7760, -122.4125 37.7790, -122.4165 37.7790, -122.4165 37.7760))'), 4326)
),
(
    'Grand University Auditorium',
    'ZONE-AUDITORIUM',
    'Keynote conference center, cultural theater, and hackathon hall.',
    'RECREATIONAL',
    TRUE,
    37.7785,
    -122.4250,
    '#a855f7',
    ST_SetSRID(ST_GeomFromText('POLYGON((-122.4270 37.7770, -122.4230 37.7770, -122.4230 37.7800, -122.4270 37.7800, -122.4270 37.7770))'), 4326)
)
ON CONFLICT (code) DO NOTHING;
