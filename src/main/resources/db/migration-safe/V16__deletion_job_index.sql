CREATE INDEX idx_users_deletion_due ON users(user_status, user_deletion_requested_at)
    WHERE user_deletion_requested_at IS NOT NULL;
