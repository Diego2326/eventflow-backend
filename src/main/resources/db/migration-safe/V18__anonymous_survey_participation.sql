CREATE TABLE survey_participation (
    participant_hash VARCHAR(64) PRIMARY KEY,
    survey_id UUID NOT NULL REFERENCES module_record(module_record_id) ON DELETE CASCADE,
    response_id UUID NOT NULL UNIQUE REFERENCES module_record(module_record_id) ON DELETE CASCADE
);
