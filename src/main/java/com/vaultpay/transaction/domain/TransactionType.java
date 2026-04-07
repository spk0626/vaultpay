package com.vaultpay.transaction.domain;

/**
 * Every transaction has exactly one type that describes what happened to the wallet.
 *
 * DEPOSIT      → money arrived from outside (bank, card, etc.)
 * WITHDRAWAL   → money left to outside
 * TRANSFER_OUT → money left to another VaultPay user's wallet (sender's perspective)
 * TRANSFER_IN  → money arrived from another VaultPay user's wallet (receiver's perspective)
 *
 * A P2P transfer always creates TWO transaction records:
 *   one TRANSFER_OUT on the sender's wallet, one TRANSFER_IN on the receiver's wallet.
 * This gives each user a complete, accurate ledger of their own wallet activity.
 *
 * Stored as STRING in the DB (matches the PostgreSQL ENUM values in V3 migration).
 */
public enum TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER_OUT,
    TRANSFER_IN
}