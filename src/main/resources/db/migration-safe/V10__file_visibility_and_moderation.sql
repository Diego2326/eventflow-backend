ALTER TABLE file_asset
    ADD COLUMN recipient_user_id UUID REFERENCES users(user_id),
    ADD COLUMN moderation_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

CREATE INDEX idx_file_asset_event_module_visibility
    ON file_asset(event_id, module_code, active, moderation_status);
