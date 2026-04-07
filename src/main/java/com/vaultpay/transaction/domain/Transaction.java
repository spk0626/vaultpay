package com.vaultpay.transaction.domain;

import com.vaultpay.common.audit.Auditable;
import com.vaultpay.wallet.domain.Wallet;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Transaction entity — a permanent, immutable ledger entry.
 *
 * DESIGN PRINCIPLES:
 *
 * 1. IMMUTABILITY — Transactions are never updated after creation.
 *    Financial history is append-only. There are no setters on this entity.
 *    If a transfer needs to be reversed, a NEW transaction of opposite type is created.
 *
 * 2. WALLET REFERENCE (not User reference) — A transaction belongs to a wallet,
 *    not a user. This correctly models the financial ledger: the wallet is the
 *    account, not the person. This also supports future multi-wallet scenarios.
 *
 * 3. IDEMPOTENCY KEY — Clients supply a unique key per operation. If the same
 *    key is submitted again (e.g. due to network retry), we return the original
 *    result instead of processing twice. The UNIQUE constraint in the DB is the
 *    ultimate guard — even if our application logic has a race, the DB prevents it.
 *
 * 4. COUNTERPARTY ID — For transfers, stores the other party's wallet ID.
 *    This lets either user query "who sent/received this?" without joining through
 *    a separate transfers table.
 *
 * 5. @Column(updatable = false) on all fields — JPA will never include these
 *    fields in an UPDATE statement. Enforces immutability at the ORM layer.
 */
@Entity
@Table(name = "transactions")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Transaction extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false, updatable = false)
    private Wallet wallet;

    /**
     * For TRANSFER_OUT: this is the receiver's wallet ID.
     * For TRANSFER_IN:  this is the sender's wallet ID.
     * NULL for DEPOSIT and WITHDRAWAL.
     */
    @Column(name = "counterparty_id", updatable = false)
    private UUID counterpartyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TransactionStatus status;

    /** Amount in cents. Always positive — the type (TRANSFER_OUT vs IN) indicates direction. */
    @Column(nullable = false, updatable = false)
    private Long amount;

    @Column(length = 500, updatable = false)
    private String description;

    /**
     * Client-generated idempotency key. Must be unique per operation.
     * The UNIQUE constraint on the DB column is the hard guarantee.
     */
    @Column(name = "idempotency_key", unique = true, updatable = false)
    private String idempotencyKey;
}