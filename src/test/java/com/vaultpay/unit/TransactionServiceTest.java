package com.vaultpay.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaultpay.common.exception.BusinessException;
import com.vaultpay.transaction.domain.*;
import com.vaultpay.transaction.dto.TransactionDtos;
import com.vaultpay.transaction.mapper.TransactionMapper;
import com.vaultpay.transaction.repository.DomainEventRepository;
import com.vaultpay.transaction.repository.TransactionRepository;
import com.vaultpay.transaction.service.TransactionService;
import com.vaultpay.user.domain.User;
import com.vaultpay.user.service.UserService;
import com.vaultpay.wallet.domain.Wallet;
import com.vaultpay.wallet.dto.WalletDtos;
import com.vaultpay.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * Unit tests for TransactionService.
 *
 * UNIT TESTING PHILOSOPHY:
 *   We test the SERVICE in isolation — all dependencies are mocked with Mockito.
 *   No real DB, no real Redis, no real RabbitMQ. This makes tests:
 *     - Fast (milliseconds, not seconds)
 *     - Deterministic (no flaky network/infra dependencies)
 *     - Focused (each test covers exactly one behaviour)
 *
 * @ExtendWith(MockitoExtension.class) → activates Mockito annotations (@Mock, @InjectMocks)
 *   without needing a full Spring context. Much faster than @SpringBootTest.
 *
 * @Nested test classes group related test cases by feature. Makes the test report
 *   readable and helps IDE navigation.
 *
 * NAMING CONVENTION: methodName_scenario_expectedOutcome
 *   e.g. transfer_whenInsufficientFunds_throwsBadRequest
 *
 * GIVEN/WHEN/THEN (BDD style) — we use Mockito's BDDMockito (given/willReturn/then)
 *   instead of the classic when/thenReturn to align with the natural English sentence structure.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionService Unit Tests")
class TransactionServiceTest {

    // ── Mocked dependencies ───────────────────────────────────────────────────
    @Mock TransactionRepository  transactionRepository;
    @Mock DomainEventRepository  domainEventRepository;
    @Mock WalletService          walletService;
    @Mock UserService            userService;
    @Mock TransactionMapper      transactionMapper;

    // ObjectMapper is NOT mocked — we use a real instance because it has no side effects
    // and mocking it would make tests brittle and verbose.
    ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks TransactionService transactionService;

    // ── Shared test fixtures ──────────────────────────────────────────────────
    User   sender;
    User   receiver;
    Wallet senderWallet;
    Wallet receiverWallet;

    @BeforeEach
    void setUp() {
        // Re-inject the real ObjectMapper because @InjectMocks creates the service
        // before we can set it manually. We use reflection via a helper.
        injectObjectMapper();

        sender   = buildUser("alice@example.com");
        receiver = buildUser("bob@example.com");

        senderWallet   = buildWallet(sender,   10_000L); // $100.00
        receiverWallet = buildWallet(receiver, 5_000L);  // $50.00
    }

    // ═════════════════════════════════════════════════════════════════════════
    // TRANSFER TESTS
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("transfer()")
    class TransferTests {

        @Test
        @DisplayName("Should debit sender and credit receiver for a valid transfer")
        void transfer_validRequest_debitsAndCredits() {
            // GIVEN
            var request = new TransactionDtos.TransferRequest(
                    receiver.getId(), 3_000L, "Dinner split", "key-001");

            setupTransferMocks();
            given(transactionRepository.findByIdempotencyKey("key-001"))
                    .willReturn(Optional.empty());
            given(transactionRepository.saveAll(anyList()))
                    .willAnswer(inv -> inv.getArgument(0));
            given(transactionMapper.toResponse(any()))
                    .willReturn(buildTransactionResponse());

            // WHEN
            transactionService.transfer(request, "alice@example.com");

            // THEN — verify both wallets were mutated correctly
            assertThat(senderWallet.getBalance()).isEqualTo(7_000L);   // 10000 - 3000
            assertThat(receiverWallet.getBalance()).isEqualTo(8_000L); // 5000  + 3000

            // Verify TWO transaction records were created (one per wallet)
            ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.forClass(List.class);
            then(transactionRepository).should().saveAll(captor.capture());
            List<Transaction> saved = captor.getValue();
            assertThat(saved).hasSize(2);
            assertThat(saved.get(0).getType()).isEqualTo(TransactionType.TRANSFER_OUT);
            assertThat(saved.get(1).getType()).isEqualTo(TransactionType.TRANSFER_IN);
        }

        @Test
        @DisplayName("Should throw 400 when sender has insufficient funds")
        void transfer_insufficientFunds_throws400() {
            // GIVEN — sender only has $100, trying to send $200
            var request = new TransactionDtos.TransferRequest(
                    receiver.getId(), 20_000L, "Too much", "key-002");

            given(userService.findByEmail("alice@example.com")).willReturn(sender);
            given(userService.findById(receiver.getId())).willReturn(receiver);
            given(walletService.findByUserId(sender.getId())).willReturn(senderWallet);
            given(walletService.findByUserId(receiver.getId())).willReturn(receiverWallet);
            given(transactionRepository.findByIdempotencyKey("key-002"))
                    .willReturn(Optional.empty());

            // WHEN / THEN
            assertThatThrownBy(() ->
                transactionService.transfer(request, "alice@example.com")
            )
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Insufficient funds");
        }

        @Test
        @DisplayName("Should throw 400 when sender tries to transfer to themselves")
        void transfer_selfTransfer_throws400() {
            // GIVEN — receiver is the same user as the sender
            var request = new TransactionDtos.TransferRequest(
                    sender.getId(), 1_000L, "Self", "key-003");

            given(userService.findByEmail("alice@example.com")).willReturn(sender);
            given(userService.findById(sender.getId())).willReturn(sender);
            given(transactionRepository.findByIdempotencyKey("key-003"))
                    .willReturn(Optional.empty());

            // WHEN / THEN
            assertThatThrownBy(() ->
                transactionService.transfer(request, "alice@example.com")
            )
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("yourself");
        }

        @Test
        @DisplayName("Should return existing result when idempotency key is reused")
        void transfer_duplicateIdempotencyKey_returnsExistingTransaction() {
            // GIVEN — the key was already used
            Transaction existing = buildTransaction(senderWallet, TransactionType.TRANSFER_OUT);
            TransactionDtos.TransactionResponse existingResponse = buildTransactionResponse();

            given(transactionRepository.findByIdempotencyKey("key-dupe"))
                    .willReturn(Optional.of(existing));
            given(transactionMapper.toResponse(existing)).willReturn(existingResponse);

            var request = new TransactionDtos.TransferRequest(
                    receiver.getId(), 1_000L, "Retry", "key-dupe");

            // WHEN
            TransactionDtos.TransactionResponse response =
                    transactionService.transfer(request, "alice@example.com");

            // THEN — returns the existing result without touching wallets
            assertThat(response).isEqualTo(existingResponse);
            then(walletService).shouldHaveNoInteractions();
            then(userService).shouldHaveNoInteractions();
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // DEPOSIT TESTS
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("deposit()")
    class DepositTests {

        @Test
        @DisplayName("Should increase wallet balance on valid deposit")
        void deposit_validRequest_creditsWallet() {
            // GIVEN
            var request = new WalletDtos.DepositRequest(5_000L, "dep-001", "Top up");

            given(transactionRepository.findByIdempotencyKey("dep-001"))
                    .willReturn(Optional.empty());
            given(userService.findByEmail("alice@example.com")).willReturn(sender);
            given(walletService.findByUserIdWithLock(sender.getId())).willReturn(senderWallet);
            given(walletService.save(senderWallet)).willReturn(senderWallet);
            given(transactionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
            given(domainEventRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
            given(transactionMapper.toResponse(any())).willReturn(buildTransactionResponse());

            long balanceBefore = senderWallet.getBalance();

            // WHEN
            transactionService.deposit(request, "alice@example.com");

            // THEN
            assertThat(senderWallet.getBalance()).isEqualTo(balanceBefore + 5_000L);
        }

        @Test
        @DisplayName("Should be idempotent when the same key is reused")
        void deposit_duplicateKey_returnsExistingWithoutModifyingBalance() {
            // GIVEN
            Transaction existing = buildTransaction(senderWallet, TransactionType.DEPOSIT);
            given(transactionRepository.findByIdempotencyKey("dep-dupe"))
                    .willReturn(Optional.of(existing));
            given(transactionMapper.toResponse(existing)).willReturn(buildTransactionResponse());

            var request = new WalletDtos.DepositRequest(5_000L, "dep-dupe", "Retry");
            long balanceBefore = senderWallet.getBalance();

            // WHEN
            transactionService.deposit(request, "alice@example.com");

            // THEN — balance unchanged, no wallet interaction
            assertThat(senderWallet.getBalance()).isEqualTo(balanceBefore);
            then(walletService).shouldHaveNoInteractions();
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // WITHDRAWAL TESTS
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("withdraw()")
    class WithdrawTests {

        @Test
        @DisplayName("Should decrease wallet balance on valid withdrawal")
        void withdraw_validRequest_debitsWallet() {
            // GIVEN
            var request = new WalletDtos.WithdrawRequest(2_000L, "wit-001", "ATM");

            given(transactionRepository.findByIdempotencyKey("wit-001"))
                    .willReturn(Optional.empty());
            given(userService.findByEmail("alice@example.com")).willReturn(sender);
            given(walletService.findByUserIdWithLock(sender.getId())).willReturn(senderWallet);
            given(walletService.save(senderWallet)).willReturn(senderWallet);
            given(transactionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
            given(domainEventRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
            given(transactionMapper.toResponse(any())).willReturn(buildTransactionResponse());

            long balanceBefore = senderWallet.getBalance();

            // WHEN
            transactionService.withdraw(request, "alice@example.com");

            // THEN
            assertThat(senderWallet.getBalance()).isEqualTo(balanceBefore - 2_000L);
        }

        @Test
        @DisplayName("Should throw 400 when withdrawing more than available balance")
        void withdraw_insufficientFunds_throws400() {
            // GIVEN — wallet has $100, trying to withdraw $200
            var request = new WalletDtos.WithdrawRequest(20_000L, "wit-002", "Too much");

            given(transactionRepository.findByIdempotencyKey("wit-002"))
                    .willReturn(Optional.empty());
            given(userService.findByEmail("alice@example.com")).willReturn(sender);
            given(walletService.findByUserIdWithLock(sender.getId())).willReturn(senderWallet);

            // WHEN / THEN
            assertThatThrownBy(() ->
                transactionService.withdraw(request, "alice@example.com")
            ).isInstanceOf(BusinessException.class)
             .hasMessageContaining("Insufficient funds");
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // TEST HELPERS
    // ═════════════════════════════════════════════════════════════════════════

    private void setupTransferMocks() {
        given(userService.findByEmail("alice@example.com")).willReturn(sender);
        given(userService.findById(receiver.getId())).willReturn(receiver);
        given(walletService.findByUserId(sender.getId())).willReturn(senderWallet);
        given(walletService.findByUserId(receiver.getId())).willReturn(receiverWallet);
        given(walletService.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(domainEventRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
    }

    private User buildUser(String email) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .password("hashed")
                .fullName("Test User")
                .build();
    }

    private Wallet buildWallet(User owner, long balance) {
        return Wallet.builder()
                .id(UUID.randomUUID())
                .user(owner)
                .balance(balance)
                .currency("USD")
                .version(0L)
                .build();
    }

    private Transaction buildTransaction(Wallet wallet, TransactionType type) {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .wallet(wallet)
                .type(type)
                .status(TransactionStatus.COMPLETED)
                .amount(1_000L)
                .build();
    }

    private TransactionDtos.TransactionResponse buildTransactionResponse() {
        return new TransactionDtos.TransactionResponse(
                UUID.randomUUID(), UUID.randomUUID(), null,
                TransactionType.TRANSFER_OUT, TransactionStatus.COMPLETED,
                1_000L, null, null,
                java.time.Instant.now()
        );
    }

    /**
     * Inject the real ObjectMapper into TransactionService via reflection.
     * Needed because @InjectMocks doesn't inject non-@Mock fields automatically.
     */
    private void injectObjectMapper() {
        try {
            var field = TransactionService.class.getDeclaredField("objectMapper");
            field.setAccessible(true);
            field.set(transactionService, objectMapper);
        } catch (Exception e) {
            throw new RuntimeException("Could not inject ObjectMapper into TransactionService", e);
        }
    }
}