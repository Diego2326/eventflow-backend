CREATE TABLE notification_read (
    notification_read_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    notification_id UUID NOT NULL REFERENCES notification(notification_id) ON DELETE CASCADE,
    read_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_notification_read_user_notification UNIQUE (user_id, notification_id)
);
CREATE INDEX idx_notification_read_user_read_at ON notification_read(user_id, read_at DESC);