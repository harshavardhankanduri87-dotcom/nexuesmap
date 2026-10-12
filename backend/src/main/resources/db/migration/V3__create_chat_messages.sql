-- ==============================================================================
-- NEXUSMAP Database Migration V3: Chat Messages Table
-- ==============================================================================

CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGSERIAL PRIMARY KEY,
    sender_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    zone_id BIGINT NOT NULL REFERENCES zones(id) ON DELETE CASCADE,
    content VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_zone_created ON chat_messages (zone_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_chat_messages_sender ON chat_messages (sender_id);
