-- ==============================================================================
-- NEXUSMAP Database Migration V4: Global Pins & Meetups
-- ==============================================================================

-- 1. Global Pins Table
CREATE TABLE IF NOT EXISTS pins (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(150) NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    location GEOGRAPHY(Point, 4326) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    creator_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- GiST Spatial Index on Pin Geography Locations
CREATE INDEX IF NOT EXISTS idx_pins_location_gist ON pins USING GIST (location);

-- B-Tree Indexes on Status, Category, Expiration, and Creator
CREATE INDEX IF NOT EXISTS idx_pins_status ON pins (status);
CREATE INDEX IF NOT EXISTS idx_pins_category ON pins (category);
CREATE INDEX IF NOT EXISTS idx_pins_expires_at ON pins (expires_at);
CREATE INDEX IF NOT EXISTS idx_pins_creator_id ON pins (creator_id);
CREATE INDEX IF NOT EXISTS idx_pins_status_expires ON pins (status, expires_at);

-- 2. Meetups Table (1-to-1 with Pins of category MEETUP)
CREATE TABLE IF NOT EXISTS meetups (
    id BIGSERIAL PRIMARY KEY,
    pin_id BIGINT NOT NULL UNIQUE REFERENCES pins(id) ON DELETE CASCADE,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE,
    capacity INT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_meetups_pin_id ON meetups (pin_id);
CREATE INDEX IF NOT EXISTS idx_meetups_start_time ON meetups (start_time);
CREATE INDEX IF NOT EXISTS idx_meetups_active ON meetups (active);

-- 3. Meetup Participants Table (M-to-N registration with capacity enforcement)
CREATE TABLE IF NOT EXISTS meetup_participants (
    id BIGSERIAL PRIMARY KEY,
    meetup_id BIGINT NOT NULL REFERENCES meetups(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_meetup_participant UNIQUE (meetup_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_meetup_participants_meetup_id ON meetup_participants (meetup_id);
CREATE INDEX IF NOT EXISTS idx_meetup_participants_user_id ON meetup_participants (user_id);
