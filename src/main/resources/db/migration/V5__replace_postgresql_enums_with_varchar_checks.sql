-- V5__replace_postgresql_enums_with_varchar_checks


ALTER TABLE transactions
    ALTER COLUMN status DROP DEFAULT;

ALTER TABLE domain_events
    ALTER COLUMN status DROP DEFAULT;

ALTER TABLE transactions
    ALTER COLUMN type TYPE VARCHAR(32) USING type::text,
    ALTER COLUMN status TYPE VARCHAR(32) USING status::text;

ALTER TABLE domain_events
    ALTER COLUMN status TYPE VARCHAR(32) USING status::text;

ALTER TABLE transactions
    ALTER COLUMN status SET DEFAULT 'PENDING';

ALTER TABLE domain_events
    ALTER COLUMN status SET DEFAULT 'PENDING';

ALTER TABLE transactions
    ADD CONSTRAINT chk_transactions_type
        CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER_IN', 'TRANSFER_OUT')),
    ADD CONSTRAINT chk_transactions_status
        CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED'));

ALTER TABLE domain_events
    ADD CONSTRAINT chk_domain_events_status
        CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'));

DROP TYPE transaction_status;
DROP TYPE transaction_type;
DROP TYPE event_status;