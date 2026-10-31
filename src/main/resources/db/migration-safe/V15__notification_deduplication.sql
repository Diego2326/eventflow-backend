ALTER TABLE notification ADD COLUMN dedupe_key VARCHAR(180);
CREATE UNIQUE INDEX ux_notification_dedupe_key ON notification(dedupe_key) WHERE dedupe_key IS NOT NULL;
