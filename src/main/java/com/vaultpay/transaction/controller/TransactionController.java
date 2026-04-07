package com.vaultpay.transaction.controller;

import com.vaultpay.transaction.dto.TransactionDtos;
import com.vaultpay.transaction.service.TransactionService;
import com.vaultpay.user.domain.User;
import com.vaultpay.wallet.dto.WalletDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for all money-movement operations and transaction history.
 *
 * DESIGN NOTE — why does this controller handle deposit/withdraw AND transfers?
 *   All three produce Transaction records and outbox events — they are fundamentally
 *   "transaction operations". Separating them into /wallets/deposit and
 *   /transactions/transfer would split a single concern across two controllers.
 *   Keeping them together under /transactions is cleaner and more RESTful.
 *
 * IDENTITY PATTERN:
 *   The authenticated user's identity is ALWAYS taken from @AuthenticationPrincipal
 *   (injected from the JWT by Spring Security). It is NEVER accepted from the request
 *   body or path variable for write operations. This prevents users from triggering
 *   operations on other users' accounts.
 *
 * @ResponseStatus on void methods sets the HTTP status code for success responses.
 * For methods returning a body, ResponseEntity or the status annotation both work.
 */
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Deposits, withdrawals, transfers, and history")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private final TransactionService transactionService;

    // ── Write Operations ──────────────────────────────────────────────────────

    @PostMapping("/deposit")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Deposit funds into your wallet",
        description = "Credit funds to the authenticated user's wallet. Supply an idempotencyKey to safely retry on network failure."
    )
    public TransactionDtos.TransactionResponse deposit(
            @Valid @RequestBody WalletDtos.DepositRequest request,
            @AuthenticationPrincipal User currentUser) {

        return transactionService.deposit(request, currentUser.getEmail());
    }

    @PostMapping("/withdraw")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Withdraw funds from your wallet",
        description = "Debit funds from the authenticated user's wallet. Returns 400 if balance is insufficient."
    )
    public TransactionDtos.TransactionResponse withdraw(
            @Valid @RequestBody WalletDtos.WithdrawRequest request,
            @AuthenticationPrincipal User currentUser) {

        return transactionService.withdraw(request, currentUser.getEmail());
    }

    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Transfer funds to another VaultPay user",
        description = """
            P2P transfer between two wallets.
            - Idempotency: re-submitting the same idempotencyKey returns the original result.
            - Concurrency: concurrent transfers from the same wallet are handled safely via optimistic locking.
            - Returns 409 if a concurrent modification conflict occurs — client should retry.
            """
    )
    public TransactionDtos.TransactionResponse transfer(
            @Valid @RequestBody TransactionDtos.TransferRequest request,
            @AuthenticationPrincipal User currentUser) {

        return transactionService.transfer(request, currentUser.getEmail());
    }

    // ── Read Operations ───────────────────────────────────────────────────────

    @GetMapping("/history")
    @Operation(
        summary = "Paginated transaction history for your wallet",
        description = "Returns transactions newest-first. Default: page 0, 20 per page."
    )
    public TransactionDtos.TransactionPageResponse getHistory(
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size, max 100", example = "20")
            @RequestParam(defaultValue = "20") int size,

            @AuthenticationPrincipal User currentUser) {

        return transactionService.getHistory(currentUser.getEmail(), page, size);
    }

    @GetMapping("/{transactionId}")
    @Operation(
        summary = "Get a single transaction by ID",
        description = "Returns 404 if the transaction doesn't exist or doesn't belong to the authenticated user."
    )
    public TransactionDtos.TransactionResponse getById(
            @PathVariable UUID transactionId,         // @PathVariable extracts the {transactionId} from the URL and converts it to a UUID object.
            @AuthenticationPrincipal User currentUser) {    // @AuthenticationPrincipal injects the currently authenticated User object based on the JWT token. This ensures we always know which user is making the request without relying on client input.

        return transactionService.getById(transactionId, currentUser.getEmail());
    }
}