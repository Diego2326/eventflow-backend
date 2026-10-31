CREATE TABLE message_read_receipt (
    message_read_receipt_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL REFERENCES conversation_message(message_id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    read_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_message_read_user_message UNIQUE (message_id, user_id)
);
CREATE INDEX idx_message_read_user_read_at ON message_read_receipt(user_id, read_at DESC);