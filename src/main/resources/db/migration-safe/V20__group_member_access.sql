ALTER TABLE guest_access_log ADD COLUMN member_index INT;
ALTER TABLE guest_access_log ADD CONSTRAINT ck_guest_access_member_index CHECK (member_index IS NULL OR member_index >= 0);
CREATE INDEX idx_guest_access_member ON guest_access_log(invitation_id, member_index, created_at);
