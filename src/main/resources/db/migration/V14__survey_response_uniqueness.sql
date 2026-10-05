CREATE UNIQUE INDEX ux_survey_response_user
    ON module_record(parent_record_id, owner_user_id)
    WHERE module_code = 'REV' AND record_type = 'SURVEY_RESPONSE'
      AND parent_record_id IS NOT NULL AND owner_user_id IS NOT NULL;
