package com.vaultpay.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;             
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Data Transfer Objects (DTOs) for the User domain.
 *
 * WHY use DTOs INSTEAD OF RETURNING ENTITIES DIRECTLY?
 *   1. SECURITY: If we returned the User entity, the JSON would include the
 *      'password' (BCrypt hash) field. DTOs let us choose exactly what to expose.
 *   2. DECOUPLING: API contracts don't break when the internal entity model changes.
 *   3. CLARITY: Each DTO has a single clear purpose — request or response.
 *
 * Java Records (Java 16+) are perfect for DTOs:
 *   - Immutable by design (all fields final)
 *   - Compact syntax — compiler auto-generates constructor, getters, equals, hashCode, toString
 *   - Lombok not needed for records
 */
public final class UserDtos {

    private UserDtos() {} // Prevent instantiation — this is just a namespace class

    // ── Request DTOs ──────────────────────────────────────────────────────────

    @Schema(description = "Registration request body")
    public record RegisterRequest(

            @Schema(example = "alice@example.com")
            @NotBlank(message = "Email is required")
            @Email(message = "Must be a valid email address")
            String email,

            @Schema(example = "SecurePass123!")             // We will validate password strength in the service layer, but we can enforce basic requirements here.
            @NotBlank(message = "Password is required")
            @Size(min = 8, message = "Password must be at least 8 characters")
            String password,

            @Schema(example = "Alice Johnson")
            @NotBlank(message = "Full name is required")
            @Size(max = 100, message = "Full name must be 100 characters or less")
            String fullName
    ) {}

    @Schema(description = "Login request body")
    public record LoginRequest(

            @Schema(example = "alice@example.com")
            @NotBlank(message = "Email is required")
            @Email(message = "Must be a valid email address")
            String email,

            @Schema(example = "SecurePass123!")
            @NotBlank(message = "Password is required")
            String password
    ) {}

    // ── Response DTOs ─────────────────────────────────────────────────────────

    @Schema(description = "Returned on successful login or registration") 
    public record AuthResponse(
            @Schema(example = "eyJhbGciOiJIUzI1NiJ9...")
            String accessToken,
            String tokenType,
            UserResponse user
    ) {
        // Convenience constructor — always sets tokenType to "Bearer". because our API always uses Bearer tokens
        public AuthResponse(String accessToken, UserResponse user) {
            this(accessToken, "Bearer", user);
        }
    }

    @Schema(description = "User profile information")
    public record UserResponse(
            UUID    id,
            String  email,
            String  fullName,
            Instant createdAt   // Instant is the modern Java date/time class for timestamps. It represents a moment on the timeline in UTC with nanosecond precision.
    ) {}
}