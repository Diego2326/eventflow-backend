CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE reservation ADD CONSTRAINT reservation_no_accepted_overlap
    EXCLUDE USING gist (
        offering_id WITH =,
        tstzrange(starts_at, ends_at, '[)') WITH &&
    ) WHERE (status = 'ACCEPTED');

CREATE UNIQUE INDEX ux_module_action_actor
    ON module_action(module_record_id, actor_user_id, action_type)
    WHERE actor_user_id IS NOT NULL;

CREATE UNIQUE INDEX ux_module_action_invitation
    ON module_action(module_record_id, invitation_id, action_type)
    WHERE invitation_id IS NOT NULL;

CREATE UNIQUE INDEX ux_user_phone
    ON users(user_phone_prefix_id, user_phone_number)
    WHERE user_phone_number IS NOT NULL;
