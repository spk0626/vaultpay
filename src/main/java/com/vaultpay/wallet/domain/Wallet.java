package com.vaultpay.wallet.domain;

import com.vaultpay.common.audit.Auditable;
import com.vaultpay.common.exception.BusinessException;
import com.vaultpay.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * The Wallet entity — maps to the "wallets" table defined in V2__create_wallets.sql.
 *
 * KEY DESIGN DECISIONS:
 *
 * 1. Balance in CENTS (Long, not Double/Float/BigDecimal).
 *    - Float/Double have rounding errors (0.1 + 0.2 ≠ 0.3 in IEEE 754).
 *    - Long cents avoids all floating-point issues with O(1) arithmetic.
 *    - $10.50 is stored as 1050L. Displayed as 10.50 in responses.
 *    - BigDecimal would also be correct but adds complexity.
 *
 * 2. @Version for Optimistic Locking.
 *    - When two concurrent transfers debit the same wallet:
 *        Thread A reads wallet (version=5), Thread B reads wallet (version=5).
 *        Thread A commits → Hibernate runs: UPDATE wallets SET balance=..., version=6 WHERE id=? AND version=5 ← succeeds
 *        Thread B commits → Hibernate runs: UPDATE wallets SET balance=..., version=6 WHERE id=? AND version=5 ← 0 rows updated → throws ObjectOptimisticLockingFailureException
 *    - No DB-level locks needed. No deadlocks. Scales well for read-heavy workloads.
 *
 * 3. debit() and credit() are domain methods on the entity itself.
 *    - Encapsulates the "balance may never go negative" invariant in one place.
 *    - Any code that modifies balance MUST go through these methods.
 *    - This is the "rich domain model" pattern (preferred over anemic models).
 */
@Entity
@Table(name = "wallets")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Wallet extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * FK to the user who owns this wallet.
     * @ManyToOne: many wallets could belong to one user (future multi-wallet support).
     * For now, UNIQUE constraint in the DB enforces one wallet per user.
     * FetchType.LAZY: don't load the full User object unless explicitly accessed.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Balance stored in minor currency units (cents). NEVER use this for display — convert to decimal. */
    @Column(nullable = false)
    private Long balance;

    /** ISO 4217 currency code, e.g. "USD" */
    @Column(nullable = false, length = 3)
    private String currency;

    /**
     * JPA Optimistic Locking version counter.
     * Hibernate auto-increments this on every UPDATE.
     * Must be Long (not long) so Hibernate can set it to null on new (transient) entities.
     */                                                  // Difference between long, Long and Int: long is a primitive type that cannot be null and has a default value of 0. Long is an object wrapper for long that can be null, which is necessary for JPA to distinguish between new (transient) entities and existing ones. If we used long, it would default to 0, which could cause issues with optimistic locking because Hibernate wouldn't know if the entity is new or existing based on the version field. By using Long, we allow it to be null for new entities, and Hibernate will set it to 1 on the first update, then increment it on subsequent updates. Int is a primitive type for integers, but it has a smaller range than long (up to 2^31-1). Since we are using UUIDs for IDs and potentially large balances in cents, it's safer to use long/Long for the version field to avoid any overflow issues. Additionally, JPA's @Version annotation typically works with Long for versioning, so it's a common convention to use Long for this purpose.
    @Version 
    @Column(nullable = false)
    private Long version;

    // ── Domain Methods ─────────────────────────────────────────────────────────

    /**
     * Debit (subtract) an amount from this wallet.
     * Enforces the invariant: balance may never go below zero.
     *
     * @param amountInCents must be positive
     * @throws BusinessException if insufficient funds
     */
    public void debit(long amountInCents) {
        if (amountInCents <= 0) {
            throw BusinessException.badRequest("Debit amount must be positive");
        }
        if (this.balance < amountInCents) {
            throw BusinessException.badRequest(
                    "Insufficient funds. Available: " + this.balance + " cents, requested: " + amountInCents + " cents"
            );
        }
        this.balance -= amountInCents;
    }

    /**
     * Credit (add) an amount to this wallet.
     *
     * @param amountInCents must be positive
     */
    public void credit(long amountInCents) {
        if (amountInCents <= 0) {
            throw BusinessException.badRequest("Credit amount must be positive");
        }
        this.balance += amountInCents;
    }
}