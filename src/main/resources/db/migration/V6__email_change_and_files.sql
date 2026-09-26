ALTER TABLE users ADD COLUMN user_pending_email VARCHAR(320);

CREATE TABLE file_asset (
    file_asset_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES event(event_id) ON DELETE CASCADE,
    uploader_user_id UUID NOT NULL REFERENCES users(user_id),
    module_code VARCHAR(8) NOT NULL REFERENCES module_catalog(module_code),
    object_path TEXT NOT NULL UNIQUE,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
