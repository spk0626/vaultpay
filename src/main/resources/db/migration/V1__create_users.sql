-- V1__create_users
-- Flyway runs these scripts in order (V1, V2, V3...) and tracks what's been applied.
-- NEVER modify a migration script after it has run — create a new one instead.

CREATE TABLE users (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email      VARCHAR(255) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,             -- BCrypt hash, never plaintext
    full_name  VARCHAR(255) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at TIMESTAMP    NOT NULL DEFAULT now()
);

-- Index on email: used for every login and user lookup
CREATE INDEX idx_users_email ON users (email);