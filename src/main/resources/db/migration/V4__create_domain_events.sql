-- V4__create_domain_events
--
-- The "Outbox Pattern":
-- Instead of publishing directly to RabbitMQ inside a DB transaction (which is unsafe
-- because the message broker and the DB are two separate systems — one can fail after
-- the other commits), we write an event record into THIS table within the SAME transaction
-- as the wallet update. A separate scheduled job then reliably reads and publishes these.
-- This guarantees: if the DB committed, the event WILL eventually be published.

CREATE TYPE event_status AS ENUM ('PENDING', 'PUBLISHED', 'FAILED');

CREATE TABLE domain_events (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,   -- e.g. 'TRANSACTION'
    aggregate_id   UUID         NOT NULL,   -- The transaction ID this event is about
    event_type     VARCHAR(100) NOT NULL,   -- e.g. 'TRANSFER_COMPLETED'
    payload        JSONB        NOT NULL,   -- Full event data as JSON
    status         event_status NOT NULL DEFAULT 'PENDING',
    created_at     TIMESTAMP    NOT NULL DEFAULT now(),
    published_at   TIMESTAMP
);

-- The scheduler polls for PENDING events — this index makes that query fast
CREATE INDEX idx_domain_events_status ON domain_events (status, created_at ASC);