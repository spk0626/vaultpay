package com.vaultpay.transaction.dto;

import com.vaultpay.transaction.domain.TransactionStatus;
import com.vaultpay.transaction.domain.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * DTOs for the Transaction domain.
 *
 * Amounts are always in cents (minor currency units) — see WalletDtos for rationale.
 */
public final class TransactionDtos {

    private TransactionDtos() {}

    // ── Request DTOs ──────────────────────────────────────────────────────────

    @Schema(description = "P2P transfer request")
    public record TransferRequest(

            @Schema(description = "The recipient user's ID", example = "b2c3d4e5-...")
            @NotNull(message = "Receiver ID is required")
            UUID receiverUserId,

            @Schema(description = "Amount in cents (e.g. 5000 = $50.00)", example = "5000")
            @NotNull(message = "Amount is required")
            @Min(value = 1, message = "Transfer amount must be at least 1 cent")
            Long amountInCents,

            @Schema(description = "Optional memo/description", example = "Dinner split")
            String description,

            @Schema(description = "Unique key to prevent duplicate transfers. Use UUID or timestamp+context.",
                    example = "transfer-2024-01-15-dinner")
            String idempotencyKey
    ) {}

    // ── Response DTOs ─────────────────────────────────────────────────────────

    @Schema(description = "A single transaction ledger entry")
    public record TransactionResponse(
            UUID              id,
            UUID              walletId,
            UUID              counterpartyId,
            TransactionType   type,
            TransactionStatus status,
            Long              amountInCents,
            String            description,
            String            idempotencyKey,
            Instant           createdAt
    ) {}

    @Schema(description = "Paginated transaction history")
    public record TransactionPageResponse(
            java.util.List<TransactionResponse> transactions,
            long   totalElements,
            int    totalPages,
            int    currentPage,
            int    pageSize
    ) {}
}