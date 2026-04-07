package com.vaultpay.transaction.repository;

import com.vaultpay.transaction.domain.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Transaction repository.
 *
 * KEY PATTERNS:
 *
 * findByWalletId + Pageable → Spring Data generates a paginated query automatically.
 *   The controller passes Pageable (page number, size, sort direction) and gets back
 *   a Page<Transaction> which contains the results plus total count metadata.
 *
 * findByIdempotencyKey → used by TransactionService to detect duplicate submissions.
 *
 * findByIdAndWalletUserId → SECURITY-CONSCIOUS query. When a user asks to see a
 *   specific transaction, we verify the transaction belongs to THEIR wallet in the
 *   same DB query (not two separate lookups). Prevents users from fetching
 *   other users' transactions by guessing UUIDs.
 */
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /**
     * Paginated transaction history for a wallet, newest first.
     * Spring Data generates: SELECT * FROM transactions WHERE wallet_id = ? ORDER BY created_at DESC
     */
    Page<Transaction> findByWalletIdOrderByCreatedAtDesc(UUID walletId, Pageable pageable);

    /**
     * Check if an idempotency key has already been used.
     */
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    /**
     * Fetch a transaction only if it belongs to the given user.
     * The JOIN through wallet → user enforces ownership at the query level.
     */
    @Query("SELECT t FROM Transaction t WHERE t.id = :id AND t.wallet.user.id = :userId")
    Optional<Transaction> findByIdAndWalletUserId(@Param("id") UUID id,
                                                  @Param("userId") UUID userId);
}