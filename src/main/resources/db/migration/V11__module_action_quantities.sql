ALTER TABLE module_action
    ADD COLUMN quantity INT NOT NULL DEFAULT 1 CHECK (quantity > 0),
    ADD COLUMN unique_action BOOLEAN NOT NULL DEFAULT TRUE;

DROP INDEX IF EXISTS ux_module_action_actor;
DROP INDEX IF EXISTS ux_module_action_invitation;

CREATE UNIQUE INDEX ux_module_action_actor_unique
    ON module_action(module_record_id, actor_user_id, action_type)
    WHERE actor_user_id IS NOT NULL AND unique_action;

CREATE UNIQUE INDEX ux_module_action_invitation_unique
    ON module_action(module_record_id, invitation_id, action_type)
    WHERE invitation_id IS NOT NULL AND unique_action;

CREATE INDEX idx_module_action_record_actor
    ON module_action(module_record_id, actor_user_id, created_at);
