CREATE TABLE push_device (
    push_device_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    expo_push_token VARCHAR(300) NOT NULL UNIQUE,
    platform VARCHAR(8) NOT NULL CHECK (platform IN ('ANDROID', 'IOS', 'WEB')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at TIMESTAMPTZ
);

CREATE INDEX idx_push_device_active_user ON push_device(user_id, updated_at DESC) WHERE revoked_at IS NULL;
