CREATE TABLE users (
    user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_name VARCHAR(100) NOT NULL,
    user_email VARCHAR(320) NOT NULL UNIQUE,
    user_phone_prefix_id INT REFERENCES phone_prefix(phone_prefix_id),
    user_phone_number varchar(32),
    user_password_hash VARCHAR(255) NOT NULL,
    user_pfp_url text
);