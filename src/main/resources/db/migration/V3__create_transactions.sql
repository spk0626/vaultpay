-- V3__create_transactions

CREATE TYPE transaction_type   AS ENUM ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER_IN', 'TRANSFER_OUT');
CREATE TYPE transaction_status AS ENUM ('PENDING', 'COMPLETED', 'FAILED');

CREATE TABLE transactions (
    id               UUID               PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id        UUID               NOT NULL,
    -- Counter-party wallet (only populated for P2P transfers, NULL for deposits/withdrawals)
    counterparty_id  UUID,
    type             transaction_type   NOT NULL,
    status           transaction_status NOT NULL DEFAULT 'PENDING',
    amount           BIGINT             NOT NULL,   -- In cents, always positive
    description      VARCHAR(500),
    -- Idempotency key: the client sends this to prevent duplicate processing
    idempotency_key  VARCHAR(255)       UNIQUE,
    created_at       TIMESTAMP          NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP          NOT NULL DEFAULT now(),

    CONSTRAINT fk_transaction_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (id),
    CONSTRAINT chk_amount_positive   CHECK (amount > 0)
);

-- Used to fetch a user's transaction history (most common query)
CREATE INDEX idx_transactions_wallet_id   ON transactions (wallet_id, created_at DESC);
-- Used to check idempotency key existence before processing
CREATE INDEX idx_transactions_idempotency ON transactions (idempotency_key);