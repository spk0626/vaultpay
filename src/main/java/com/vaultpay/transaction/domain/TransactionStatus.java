package com.vaultpay.transaction.domain;

/**
 * Lifecycle status of a transaction.
 *
 * PENDING   → created but not yet fully processed (reserved for async flows)
 * COMPLETED → successfully processed, balance updated
 * FAILED    → processing failed; balance was NOT changed (or rolled back)
 *
 * In our synchronous implementation all transactions are created as COMPLETED
 * within a single DB transaction. The PENDING state would be used if we
 * added an async two-phase commit or external payment gateway integration.
 */
public enum TransactionStatus {
    PENDING,
    COMPLETED,
    FAILED
}