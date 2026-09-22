CREATE TABLE phone_prefix(
    phone_prefix_id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    phone_prefix_country_code varchar(2) not null unique,
    phone_prefix varchar(10) NOT NULL
);