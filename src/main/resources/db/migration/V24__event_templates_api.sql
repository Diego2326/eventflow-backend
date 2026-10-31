CREATE TABLE event_template (
    event_template_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    template_name VARCHAR(120) NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    description TEXT,
    module_codes TEXT NOT NULL DEFAULT '[]',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_event_template_owner_updated ON event_template(owner_user_id, updated_at DESC);
