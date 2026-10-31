-- Execute AFTER safe V1-V27 as eventflow_migrator. Transactional; no managed roles.
\set ON_ERROR_STOP on
BEGIN;
DO $$
DECLARE item record;
BEGIN
 IF current_user <> 'eventflow_migrator' THEN RAISE EXCEPTION 'Use the dedicated migrator owner'; END IF;
 FOR item IN SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename IN
 ('users','user_role','auth_token','user_session','notification_preference','event','event_collaborator','event_module','marketplace_offering','offering_availability','reservation','simulated_payment','invitation','agenda_item','agenda_favorite','assistance_request','notification','conversation_message','module_record','module_action','review','moderation_report','role_request','file_asset','survey_participation','ticket_type','ticket_order','event_ticket','push_device','notification_read','event_template','user_privacy_preference','message_read_receipt') LOOP
  EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE ON TABLE public.%I TO eventflow_runtime',item.tablename);
 END LOOP;
END $$;
GRANT SELECT ON public.country,public.phone_prefix,public.module_dependency TO eventflow_runtime;
GRANT SELECT,UPDATE ON public.module_catalog TO eventflow_runtime;
GRANT SELECT,INSERT ON public.audit_log,public.guest_access_log,public.ticket_access_log,public.ticket_settings_audit TO eventflow_runtime;
REVOKE ALL ON TABLE public.flyway_schema_history FROM eventflow_runtime;
-- Current mutable business IDs are UUIDs; the only identity sequence belongs to
-- read-only phone_prefix; runtime requires no sequence grant in V1-V27.
COMMIT;
