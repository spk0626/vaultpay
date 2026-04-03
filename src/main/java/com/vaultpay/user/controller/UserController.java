package com.vaultpay.user.controller;

import com.vaultpay.user.dto.UserDtos;
import com.vaultpay.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST Controller for user authentication and profile management.
 *
 * @RestController = @Controller + @ResponseBody
 *   All return values are serialized to JSON automatically.
 *
 * @RequestMapping sets the base path for all methods in this class.
 *
 * ResponseEntity<T> gives us full control over the HTTP response:
 *   status code, headers, and body.
 *
 * @Valid on method parameters triggers Jakarta Bean Validation on the request body.
 *   If validation fails, Spring throws MethodArgumentNotValidException, which our
 *   GlobalExceptionHandler catches and converts to a 400 response.
 *
 * @AuthenticationPrincipal → Spring injects the currently authenticated UserDetails
 *   (our User object) — no need to parse the JWT manually in controllers.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, login, and profile endpoints")
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new user and create their wallet")
    public UserDtos.AuthResponse register(@Valid @RequestBody UserDtos.RegisterRequest request) {
        return userService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate and receive a JWT token")
    public UserDtos.AuthResponse login(@Valid @RequestBody UserDtos.LoginRequest request) {
        return userService.login(request);
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get the currently authenticated user's profile")
    public UserDtos.UserResponse getCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails) {
        return userService.findByEmailAsDto(userDetails.getUsername());
    }

    @GetMapping("/users/{userId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get a user profile by ID")
    public UserDtos.UserResponse getUserById(@PathVariable UUID userId) {
        return userService.getUserById(userId);
    }
}