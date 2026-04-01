-- V2__create_wallets

CREATE TABLE wallets (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL UNIQUE,      -- One wallet per user
    balance    BIGINT       NOT NULL DEFAULT 0,    -- Stored in CENTS, never Float/Double
    currency   VARCHAR(3)   NOT NULL DEFAULT 'USD',
    version    BIGINT       NOT NULL DEFAULT 0,    -- For optimistic locking (@Version in JPA)
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT fk_wallet_user     FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    -- CRITICAL: The database enforces the balance can NEVER go negative.
    -- Even if there's a bug in application code, the DB acts as a safety net.
    CONSTRAINT chk_balance_non_negative CHECK (balance >= 0)
);

CREATE INDEX idx_wallets_user_id ON wallets (user_id);