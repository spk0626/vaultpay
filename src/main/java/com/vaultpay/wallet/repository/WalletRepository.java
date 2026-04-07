package com.vaultpay.wallet.repository;

import com.vaultpay.wallet.domain.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Wallet persistence operations.
 *
 * We add one custom query method beyond the standard JpaRepository methods:
 *
 * findByUserId → Spring Data generates:
 *   SELECT * FROM wallets WHERE user_id = ?
 *
 * findByUserIdWithPessimisticLock → used for deposit/withdraw operations
 * where we want a database-level lock (FOR UPDATE) to prevent lost updates.
 * This is different from optimistic locking (used for transfers) — for single-wallet
 * operations like deposit, there is no competing version to compare against,
 * so a pessimistic lock gives a simpler, guaranteed-correct result.
 *
 * WHY TWO LOCKING STRATEGIES:
 *   - Deposits/Withdrawals: only one wallet involved → pessimistic lock (simple, safe)  
 *   - Transfers: two wallets involved → optimistic lock (avoids deadlock risk)
 */

 // Difference between optimistic and pessimistic locking: 
 // Optimistic locking allows multiple transactions to access the same data concurrently, but checks for conflicts before committing. If a conflict is detected (e.g., another transaction has modified the data), it throws an exception, and the transaction can be retried. 
// Pessimistic locking, on the other hand, locks the data at the database level as soon as it is accessed, preventing other transactions from modifying it until the lock is released. This can lead to better performance in scenarios with low contention but can cause deadlocks if not used carefully. 
// In our case, we use optimistic locking for transfers because they involve two wallets and have a higher risk of contention, while we use pessimistic locking for deposits/withdrawals since they only involve one wallet and are less likely to cause contention.

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.user.id = :userId")     // @Query allows us to write a custom JPQL query. In this case, we select the Wallet entity where the associated User's id matches the provided userId parameter. The @Lock annotation with LockModeType.PESSIMISTIC_WRITE tells Spring Data JPA to acquire a pessimistic write lock on the selected wallet row in the database, which prevents other transactions from modifying it until the lock is released.
    Optional<Wallet> findByUserIdWithPessimisticLock(@Param("userId") UUID userId);   // @Param is used to bind the method parameter "userId" to the named parameter ":userId" in the JPQL query. This allows us to pass the userId value when calling this method, and it will be correctly substituted into the query when executed against the database.
}


// How is findByUserIdWithPessimisticLock() different from findByUserId()?
// findByUserId() is a standard query method that retrieves a wallet based on the userId without acquiring any locks. It allows multiple transactions to read the same wallet concurrently, which is suitable for operations that do not modify the wallet's state (e.g., checking the balance).
// findByUserIdWithPessimisticLock(), on the other hand, is a custom query method that retrieves a wallet based on the userId while acquiring a pessimistic write lock on the selected row in the database. This means that when this method is called, it will prevent other transactions from modifying the same wallet until the lock is released. This is particularly important for operations that involve modifying the wallet's balance (e.g., deposits or withdrawals) to ensure data integrity and prevent lost updates in concurrent scenarios.
// why userId Param and query only in findByUserIdWithPessimisticLock()?
// The findByUserId() method relies on Spring Data JPA's method naming convention to automatically generate the query based on the method name. Since it follows the standard naming pattern, Spring Data can infer the query without needing an explicit @Query annotation.
// In contrast, findByUserIdWithPessimisticLock() requires a custom query because we need to specify the locking behavior (pessimistic write lock) that is not part of the standard method naming convention. The @Query annotation allows us to define the JPQL query explicitly, and the @Lock annotation specifies the locking strategy to be applied when executing that query. This is why we have an explicit query and parameter binding in findByUserIdWithPessimisticLock(), while findByUserId() can rely on Spring Data's automatic query generation.
