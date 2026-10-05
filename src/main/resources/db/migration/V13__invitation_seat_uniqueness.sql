CREATE UNIQUE INDEX ux_invitation_active_seat
    ON invitation(event_id, lower(table_label), lower(seat_label))
    WHERE revoked_at IS NULL AND seat_label IS NOT NULL AND table_label IS NOT NULL;
