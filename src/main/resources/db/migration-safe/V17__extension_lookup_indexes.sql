CREATE INDEX idx_module_record_upcoming
    ON module_record(module_code, record_type, status, starts_at)
    WHERE starts_at IS NOT NULL;

CREATE INDEX idx_module_action_actor_type
    ON module_action(actor_user_id, action_type, created_at)
    WHERE actor_user_id IS NOT NULL;
