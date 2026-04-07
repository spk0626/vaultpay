package com.vaultpay.wallet.controller;

import com.vaultpay.user.domain.User;
import com.vaultpay.wallet.dto.WalletDtos;
import com.vaultpay.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for wallet read operations.
 *
 * Write operations (deposit, withdraw, transfer) are in TransactionController
 * because they always produce Transaction records — they are transaction operations
 * that happen to affect wallet balances, not wallet operations per se.
 *
 * @AuthenticationPrincipal User → Spring injects the full authenticated User entity
 * (from the SecurityContext set by JwtAuthenticationFilter). We cast to our User class
 * because loadUserByUsername() returns our User, which implements UserDetails.
 */
@RestController   // @RestController = @Controller + @ResponseBody → all methods return JSON responses.
@RequestMapping("/api/v1/wallets")   // Base path for all wallet-related endpoints
@RequiredArgsConstructor
@Tag(name = "Wallets", description = "Wallet balance and details")
@SecurityRequirement(name = "bearerAuth")     // All endpoints in this controller require authentication via the bearerAuth scheme defined in our OpenAPI config.
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/me")
    @Operation(summary = "Get the authenticated user's wallet details and balance")
    public WalletDtos.WalletResponse getMyWallet(
            @AuthenticationPrincipal User currentUser) {              // Spring injects the currently authenticated User entity here, so we can directly access currentUser.getId() to fetch their wallet.
        return walletService.getWalletByUserId(currentUser.getId());  // We didn't define a custom getId() method in User entity
    } // getId() means the getter generated for the id field in the User entity by Lombok's @Getter annotation. Since User is a JPA entity with an @Id field named id, Lombok generates a getId() method that returns the value of the id field. This allows us to call currentUser.getId() to retrieve the user's unique identifier when fetching their wallet details.
}