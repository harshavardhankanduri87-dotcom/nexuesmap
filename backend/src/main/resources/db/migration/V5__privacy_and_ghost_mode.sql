-- ==============================================================================
-- NEXUSMAP Database Migration V5: Privacy Settings & Ephemeral Retention
-- ==============================================================================

-- 1. Add privacy configuration columns to users table
ALTER TABLE users 
    ADD COLUMN IF NOT EXISTS location_sharing_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS fuzzing_radius_meters INT NOT NULL DEFAULT 100;

-- 2. Add indexing for efficient ephemeral location pruning and user lookup
CREATE INDEX IF NOT EXISTS idx_users_ghost_mode ON users (ghost_mode);
CREATE INDEX IF NOT EXISTS idx_user_locations_prune ON user_locations (recorded_at);
