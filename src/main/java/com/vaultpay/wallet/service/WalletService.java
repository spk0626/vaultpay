package com.vaultpay.wallet.service;

import com.vaultpay.common.exception.BusinessException;
import com.vaultpay.user.domain.User;
import com.vaultpay.wallet.domain.Wallet;
import com.vaultpay.wallet.dto.WalletDtos;
import com.vaultpay.wallet.mapper.WalletMapper;
import com.vaultpay.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * WalletService manages wallet lifecycle: creation and balance queries.
 *
 * Deposit and withdrawal operations live in TransactionService — they produce
 * Transaction records and domain events, which is not WalletService's concern.
 * This is to have the Single Responsibility Principle.
 *
 * Package-level methods (without @Transactional) are called FROM TransactionService
 * which already runs inside a transaction — the calls participate in the
 * caller's transaction automatically.
 */
@Slf4j          // Lombok annotation to generate a logger for this class. 
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletMapper     walletMapper;

    /**
     * Creates a wallet for a newly registered user.
     * Called by UserService.register() within the same @Transactional block.
     * The wallet starts with a zero balance in LKR.
     */
    @Transactional                                  // ensures that if wallet creation fails, the user record is rolled back. This keeps our data consistent — we won't have users without wallets.
    public Wallet createWallet(User user) {
        Wallet wallet = Wallet.builder()
                .user(user)
                .balance(0L)
                .currency("LKR")
                .version(0L)
                .build();

        Wallet saved = walletRepository.save(wallet);
        log.info("Wallet created for user: {}", user.getEmail());
        return saved;
    }

    /**
     * Get a user's wallet details. Accessible via the API.
     */
    @Transactional(readOnly = true)                                 // readOnly = true optimizes the transaction for read operations — it can help with performance and also signals our intent that this method won't modify the database.
    public WalletDtos.WalletResponse getWalletByUserId(UUID userId) {
        Wallet wallet = findByUserId(userId);
        return walletMapper.toWalletResponse(wallet);
    }

    // ── Package-accessible helpers used by TransactionService ─────────────────

    /**
     * Load a wallet by its owner's userId.
     * Used by TransactionService — does NOT acquire a lock (TransactionService decides which locking strategy to apply for each operation type).
     */
    public Wallet findByUserId(UUID userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> BusinessException.notFound(
                        "Wallet not found for user: " + userId));
    }

    /**
     * Load a wallet with a database-level pessimistic write lock.
     * Used for deposit and withdrawal — single-wallet operations where we must guarantee exclusive access for the duration of the transaction.
     */
    public Wallet findByUserIdWithLock(UUID userId) {
        return walletRepository.findByUserIdWithPessimisticLock(userId)
                .orElseThrow(() -> BusinessException.notFound(
                        "Wallet not found for user: " + userId));
    }

    /**
     * Persist wallet changes. Called by TransactionService after mutating balance.
     */
    public Wallet save(Wallet wallet) {
        return walletRepository.save(wallet);
    }
}