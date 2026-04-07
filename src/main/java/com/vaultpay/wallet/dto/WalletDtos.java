package com.vaultpay.wallet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * DTOs for the Wallet domain.
 *
 * NOTE ON AMOUNTS:
 *   All monetary amounts are in CENTS (minor currency units).
 *   The API intentionally works in cents to avoid floating-point ambiguity.
 *
 *   $10.50 = 1050 (cents)
 *   $1.00  = 100 (cents)
 *
 *   Clients are responsible for converting to their display format.
 *   This is the same convention used by Stripe and other fintech APIs.
 */
public final class WalletDtos {

    private WalletDtos() {}

    // ── Request DTOs ──────────────────────────────────────────────────────────

    @Schema(description = "Deposit funds into a wallet")  // Swagger/OpenAPI annotation to describe this DTO in the generated API docs
    public record DepositRequest(

            @Schema(description = "Amount in cents (e.g. 1000 = $10.00)", example = "5000")
            @NotNull(message = "Amount is required")
            @Min(value = 1, message = "Amount must be at least 1 cent")
            Long amountInCents,

            @Schema(description = "Client-supplied idempotency key to prevent duplicate deposits",
                    example = "deposit-abc123-2024-01-15")
            String idempotencyKey,

            @Schema(description = "Optional description for this deposit", example = "Monthly top-up")
            String description
    ) {}

    @Schema(description = "Withdraw funds from a wallet")
    public record WithdrawRequest(

            @Schema(description = "Amount in cents (e.g. 1000 = $10.00)", example = "2000")
            @NotNull(message = "Amount is required")
            @Min(value = 1, message = "Amount must be at least 1 cent")
            Long amountInCents,

            @Schema(description = "Client-supplied idempotency key to prevent duplicate withdrawals",
                    example = "withdraw-xyz789-2024-01-15")
            String idempotencyKey,

            @Schema(description = "Optional description", example = "ATM withdrawal")
            String description
    ) {}

    // ── Response DTOs ─────────────────────────────────────────────────────────

    @Schema(description = "Wallet details and current balance")
    public record WalletResponse(
            UUID    id,
            UUID    userId,
            Long    balanceInCents,
            String  currency,
            Instant createdAt
    ) {}
}