-- Optional, per-user APPROVAL REQUIRED. Never assigns roles or verifies email.
-- psql ... -v user_id=<approved-uuid> -v target_status=PENDING_VERIFICATION -v apply=false -f this-file
\set ON_ERROR_STOP on
\if :{?apply}
\else
\set apply false
\endif
BEGIN;
SELECT set_config('eventflow.transition_user', :'user_id', true);
SELECT set_config('eventflow.transition_status', :'target_status', true);
DO $$
DECLARE desired text:=current_setting('eventflow.transition_status'); target uuid:=current_setting('eventflow.transition_user')::uuid;
BEGIN
 IF desired NOT IN ('PENDING_VERIFICATION','DISABLED','ACTIVE') THEN RAISE EXCEPTION 'Unapproved transition state'; END IF;
 IF NOT EXISTS(SELECT FROM public.users WHERE user_id=target) THEN RAISE EXCEPTION 'User missing'; END IF;
 IF EXISTS(SELECT FROM public.users WHERE user_id=target AND user_status IN ('DELETED','DELETION_PENDING')) THEN RAISE EXCEPTION 'Do not reactivate deleted/deleting users'; END IF;
 IF desired='ACTIVE' AND EXISTS(SELECT FROM public.users WHERE user_id=target AND user_email_verified_at IS NULL) THEN RAISE EXCEPTION 'Verify through the real email flow first'; END IF;
 UPDATE public.users SET user_status=desired,user_updated_at=now() WHERE user_id=target;
END $$;
\if :apply
COMMIT;
\else
ROLLBACK;
\endif
