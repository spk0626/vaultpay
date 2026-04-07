package com.vaultpay.transaction.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaultpay.common.exception.BusinessException;
import com.vaultpay.transaction.domain.*;
import com.vaultpay.transaction.dto.TransactionDtos;
import com.vaultpay.transaction.mapper.TransactionMapper;
import com.vaultpay.transaction.repository.DomainEventRepository;
import com.vaultpay.transaction.repository.TransactionRepository;
import com.vaultpay.user.domain.User;
import com.vaultpay.user.service.UserService;
import com.vaultpay.wallet.domain.Wallet;
import com.vaultpay.wallet.dto.WalletDtos;
import com.vaultpay.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * TransactionService — the financial core of VaultPay.
 *
 * This class handles all money movement: deposits, withdrawals, and P2P transfers.
 * Every operation is designed around three non-negotiable correctness properties:
 *
 * 1. ATOMICITY   — the DB operation is all-or-nothing via @Transactional.
 *                  If balance update succeeds but event write fails → both are rolled back.
 *                  Money never moves without a corresponding transaction record.
 *
 * 2. IDEMPOTENCY — clients supply an idempotency key. If the same key is submitted
 *                  again (network timeout, client retry), we return the original result
 *                  without processing again. This prevents double-charges.
 *
 * 3. CONCURRENCY SAFETY
 *   - Deposits/Withdrawals use PESSIMISTIC locking (SELECT FOR UPDATE at DB level).
 *     Safe for single-wallet operations. Simple and guaranteed.
 *   - Transfers use OPTIMISTIC locking (@Version on Wallet entity).
 *     If two concurrent transfers from the same wallet race, the DB @Version field
 *     detects the conflict at commit time → ObjectOptimisticLockingFailureException →
 *     GlobalExceptionHandler returns 409 → client retries. No deadlocks possible.
 *
 * WHY SEPARATE LOCKING STRATEGIES?
 *   Pessimistic locks on TWO wallets in the same transaction risk DEADLOCK if two
 *   transfers run concurrently with reversed sender/receiver. Optimistic locking
 *   avoids this entirely. For single-wallet ops (deposit/withdraw) there's only one
 *   wallet to lock, so pessimistic locking is simpler and more efficient.
 */
@Slf4j
@Service
@RequiredArgsConstructor                           // Lombok generates a constructor with all final fields (the dependencies)
public class TransactionService {

    private final TransactionRepository  transactionRepository;
    private final DomainEventRepository  domainEventRepository;
    private final WalletService          walletService;
    private final UserService            userService;
    private final TransactionMapper      transactionMapper;
    private final ObjectMapper           objectMapper;   // Spring auto-creates this Jackson bean. 

    // ─────────────────────────────────────────────────────────────────────────
    // DEPOSIT
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Deposit funds into the authenticated user's wallet.
     *
     * @param request   the deposit amount, optional idempotency key, and description
     * @param userEmail the authenticated user (from JWT — not from request body, never trust the client for identity)
     */
    @Transactional
    public TransactionDtos.TransactionResponse deposit(WalletDtos.DepositRequest request,
                                                       String userEmail) {
        // ── Step 1: Idempotency check ──────────────────────────────────────────
        if (request.idempotencyKey() != null) {
            var existing = transactionRepository.findByIdempotencyKey(request.idempotencyKey());
            if (existing.isPresent()) {
                log.info("Idempotent deposit — returning existing transaction: {}", request.idempotencyKey());
                return transactionMapper.toResponse(existing.get());
            }
        }

        // ── Step 2: Load wallet with DB-level lock ────────────────────────────
        User   sender = userService.findByEmail(userEmail);
        Wallet wallet = walletService.findByUserIdWithLock(sender.getId());

        // ── Step 3: Mutate balance via domain method (enforces non-negative invariant) ──
        wallet.credit(request.amountInCents());
        walletService.save(wallet);

        // ── Step 4: Record the ledger entry ───────────────────────────────────
        Transaction tx = Transaction.builder()
                .wallet(wallet)
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.COMPLETED)
                .amount(request.amountInCents())
                .description(request.description())
                .idempotencyKey(request.idempotencyKey())
                .build();
        tx = transactionRepository.save(tx);

        // ── Step 5: Write outbox event (same transaction — guaranteed consistency) ──
        publishEvent("DEPOSIT_COMPLETED", tx.getId(), Map.of(
                "userId",       sender.getId(),
                "walletId",     wallet.getId(),
                "amountInCents", request.amountInCents()
        ));

        log.info("Deposit of {} cents to wallet {} completed", request.amountInCents(), wallet.getId());
        return transactionMapper.toResponse(tx);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WITHDRAWAL
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Withdraw funds from the authenticated user's wallet.
     * The wallet.debit() domain method enforces that balance cannot go negative.
     */
    @Transactional
    public TransactionDtos.TransactionResponse withdraw(WalletDtos.WithdrawRequest request,
                                                        String userEmail) {
        // ── Idempotency check ─────────────────────────────────────────────────
        if (request.idempotencyKey() != null) {
            var existing = transactionRepository.findByIdempotencyKey(request.idempotencyKey());
            if (existing.isPresent()) {
                log.info("Idempotent withdrawal — returning existing transaction: {}", request.idempotencyKey());
                return transactionMapper.toResponse(existing.get());
            }
        }

        User   sender = userService.findByEmail(userEmail);
        Wallet wallet = walletService.findByUserIdWithLock(sender.getId());

        // debit() throws BusinessException(400) if insufficient funds
        wallet.debit(request.amountInCents());
        walletService.save(wallet);

        Transaction tx = Transaction.builder()
                .wallet(wallet)
                .type(TransactionType.WITHDRAWAL)
                .status(TransactionStatus.COMPLETED)
                .amount(request.amountInCents())
                .description(request.description())
                .idempotencyKey(request.idempotencyKey())
                .build();
        tx = transactionRepository.save(tx);

        publishEvent("WITHDRAWAL_COMPLETED", tx.getId(), Map.of(
                "userId",       sender.getId(),
                "walletId",     wallet.getId(),
                "amountInCents", request.amountInCents()
        ));

        log.info("Withdrawal of {} cents from wallet {} completed", request.amountInCents(), wallet.getId());
        return transactionMapper.toResponse(tx);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // P2P TRANSFER  ← The most complex and interesting operation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Transfer funds between two VaultPay users.
     *
     * FULL FLOW:
     *   1. Idempotency check — already processed? return existing result.
     *   2. Load sender & receiver (fail fast if either not found).
     *   3. Guard: sender cannot transfer to themselves.
     *   4. Load BOTH wallets (no locks — optimistic locking via @Version handles concurrency).
     *   5. Validate sender has sufficient balance.
     *   6. Debit sender, credit receiver via domain methods.
     *   7. Save both updated wallets — if @Version conflict, Hibernate throws
     *      ObjectOptimisticLockingFailureException → caught by GlobalExceptionHandler → 409.
     *   8. Create TWO transaction records (one per wallet ledger entry).
     *   9. Write one outbox event covering the full transfer.
     *  10. Return the sender's transaction record.
     *
     * ALL steps 4-9 run inside a single @Transactional — they all commit or all roll back.
     *
     * @param request   the transfer details including receiver, amount, idempotency key
     * @param senderEmail extracted from the authenticated user's JWT — not from request body
     */
    @Transactional
    public TransactionDtos.TransactionResponse transfer(TransactionDtos.TransferRequest request,
                                                        String senderEmail) {
        // ── Step 1: Idempotency check ──────────────────────────────────────────
        if (request.idempotencyKey() != null) {
            var existing = transactionRepository.findByIdempotencyKey(request.idempotencyKey());
            if (existing.isPresent()) {
                log.info("Idempotent transfer — returning existing transaction: {}", request.idempotencyKey());
                return transactionMapper.toResponse(existing.get());
            }
        }

        // ── Step 2: Load actors ────────────────────────────────────────────────
        User sender   = userService.findByEmail(senderEmail);
        User receiver = userService.findById(request.receiverUserId());

        // ── Step 3: Self-transfer guard ────────────────────────────────────────
        if (sender.getId().equals(receiver.getId())) {
            throw BusinessException.badRequest("Cannot transfer funds to yourself");
        }

        // ── Step 4: Load wallets (no DB locks — @Version handles concurrent writes) ──
        Wallet senderWallet   = walletService.findByUserId(sender.getId());
        Wallet receiverWallet = walletService.findByUserId(receiver.getId());

        // ── Step 5: Business validation (balance check before mutation) ────────
        if (senderWallet.getBalance() < request.amountInCents()) {
            throw BusinessException.badRequest(
                    "Insufficient funds. Available: " + senderWallet.getBalance()
                    + " cents, requested: " + request.amountInCents() + " cents");
        }

        // ── Step 6: Mutate balances via domain methods ─────────────────────────
        senderWallet.debit(request.amountInCents());
        receiverWallet.credit(request.amountInCents());

        // ── Step 7: Persist both wallets ───────────────────────────────────────
        // If another concurrent transfer already modified senderWallet's @Version,
        // one of these saves will throw ObjectOptimisticLockingFailureException at commit.
        walletService.save(senderWallet);
        walletService.save(receiverWallet);

        // ── Step 8: Create two immutable ledger entries ────────────────────────
        Transaction senderTx = Transaction.builder()
                .wallet(senderWallet)
                .counterpartyId(receiverWallet.getId())
                .type(TransactionType.TRANSFER_OUT)
                .status(TransactionStatus.COMPLETED)
                .amount(request.amountInCents())
                .description(request.description())
                .idempotencyKey(request.idempotencyKey())  // Key only on sender's record
                .build();

        Transaction receiverTx = Transaction.builder()
                .wallet(receiverWallet)
                .counterpartyId(senderWallet.getId())
                .type(TransactionType.TRANSFER_IN)
                .status(TransactionStatus.COMPLETED)
                .amount(request.amountInCents())
                .description(request.description())
                .idempotencyKey(null)   // No key on receiver's copy — it's the same logical event
                .build();

        List<Transaction> saved = transactionRepository.saveAll(List.of(senderTx, receiverTx));
        Transaction savedSenderTx = saved.get(0);       // why not get senderTx directly? because saveAll returns the saved entities with generated IDs populated, and we need the sender's transaction ID for the outbox event.

        // ── Step 9: Outbox event — published asynchronously by DomainEventPublisher ──
        publishEvent("TRANSFER_COMPLETED", savedSenderTx.getId(), Map.of(
                "senderId",       sender.getId(),
                "receiverId",     receiver.getId(),
                "senderWalletId",   senderWallet.getId(),
                "receiverWalletId", receiverWallet.getId(),
                "amountInCents",  request.amountInCents(),
                "description",    request.description() != null ? request.description() : ""
        ));

        log.info("Transfer of {} cents from {} to {} completed",
                request.amountInCents(), sender.getEmail(), receiver.getEmail());

        return transactionMapper.toResponse(savedSenderTx);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HISTORY
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Paginated transaction history for the authenticated user's wallet.
     *
     * readOnly = true → Hibernate skips dirty-checking overhead on all loaded entities.
     * Use this on ALL queries that don't mutate data.
     */
    @Transactional(readOnly = true)
    public TransactionDtos.TransactionPageResponse getHistory(String userEmail, int page, int size) {
        User   user   = userService.findByEmail(userEmail);
        Wallet wallet = walletService.findByUserId(user.getId());

        Pageable pageable = PageRequest.of(page, Math.min(size, 100)); // cap at 100 per page
        Page<Transaction> txPage = transactionRepository
                .findByWalletIdOrderByCreatedAtDesc(wallet.getId(), pageable);

        List<TransactionDtos.TransactionResponse> items = txPage.getContent()
                .stream()
                .map(transactionMapper::toResponse)
                .toList();

        return new TransactionDtos.TransactionPageResponse(
                items,
                txPage.getTotalElements(),
                txPage.getTotalPages(),
                txPage.getNumber(),
                txPage.getSize()
        );
    }

    /**
     * Fetch a single transaction by ID, enforcing that it belongs to the requesting user.
     */
    @Transactional(readOnly = true)
    public TransactionDtos.TransactionResponse getById(UUID transactionId, String userEmail) {
        User user = userService.findByEmail(userEmail);
        Transaction tx = transactionRepository.findByIdAndWalletUserId(transactionId, user.getId())
                .orElseThrow(() -> BusinessException.notFound("Transaction not found"));
        return transactionMapper.toResponse(tx);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Serialize the event payload and write to the outbox table. Serialize means convert the payload Map into a JSON string to store in the DB. The DomainEventPublisher will later read these events, deserialize the JSON back into a Map, and publish to RabbitMQ.
     * This MUST be called inside an active @Transactional — the event is committed
     * atomically with the wallet and transaction changes.
     */
    private void publishEvent(String eventType, UUID aggregateId, Map<String, Object> payload) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            DomainEvent event = DomainEvent.builder()
                    .aggregateType("TRANSACTION")
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .payload(payloadJson)
                    .status(EventStatus.PENDING)
                    .build();
            domainEventRepository.save(event);
        } catch (JsonProcessingException e) {
            // This should never happen with a Map<String, Object> payload,
            // but we must handle the checked exception. Wrap and rethrow to
            // roll back the entire transaction — an event-less transfer is unacceptable.
            throw new IllegalStateException("Failed to serialize domain event payload", e);
        }
    }
}