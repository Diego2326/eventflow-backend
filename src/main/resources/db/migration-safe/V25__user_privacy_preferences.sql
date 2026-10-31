CREATE TABLE user_privacy_preference (
    user_id UUID PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    networking_profile_visible BOOLEAN NOT NULL DEFAULT TRUE,
    networking_contact_visible BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);