package com.vaultpay.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralized exception handler for ALL controllers.
 *
 * @RestControllerAdvice → applies to every @RestController in the application.
 *
 * WHY THIS PATTERN:
 * Without this, every exception produces a different response shape (some have "message",
 * some have "error", some have "errors"). APIs should have consistent error responses.
 *
 * ProblemDetail (RFC 7807) is the STANDARD format for HTTP error responses. Spring 6 has it built-in
 *
 * Example response body:
 * {
 *   "type": "about:blank",
 *   "title": "Bad Request",
 *   "status": 400,
 *   "detail": "Insufficient funds",
 *   "instance": "/api/v1/transactions/transfer",
 *   "timestamp": "2024-01-15T10:30:00Z"
 * }
 */

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles optimistic locking failures from concurrent wallet updates.
     * It means if two transfers from the same wallet raced —> one won, one must retry.
     */
    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLocking(org.springframework.orm.ObjectOptimisticLockingFailureException ex) {
        log.warn("Concurrent modification conflict: {}", ex.getMessage());
        return buildProblemDetail(HttpStatus.CONFLICT,
                "This resource was modified concurrently. Please retry your request.");
    }

    /**
     * Handles our custom domain exceptions.
     * The status code is embedded in the exception itself.
     */
    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusinessException(BusinessException ex) {
        log.warn("Business rule violation: {}", ex.getMessage());
        return buildProblemDetail(ex.getStatus(), ex.getMessage());
    }

    /**
     * Handles @Valid / @Validated failures on request bodies.
     * Collects all field errors into a structured map.
     *
     * Example: { "email": "must not be blank", "amount": "must be positive" }
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid",
                        // If two errors for the same field, keep the first
                        (existing, replacement) -> existing
                ));

        ProblemDetail pd = buildProblemDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        pd.setProperty("errors", errors);
        return pd;
    }

    /**
     * Handles wrong password / missing token scenarios.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        // Note: we deliberately return a vague message for security — we don't reveal whether the email exists or the password was wrong.
        return buildProblemDetail(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    /**
     * Handles Spring Security access denied (authenticated user lacks permission).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return buildProblemDetail(HttpStatus.FORBIDDEN, "Access denied");
    }

    /**
     * Catch-all for any unexpected exception.
     * Logs the full stack trace (important!) but returns a safe, generic message to the client.
     * Never expose internal stack traces to API consumers.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);
        return buildProblemDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An internal error occurred");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private ProblemDetail buildProblemDetail(HttpStatus status, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create("about:blank"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}
