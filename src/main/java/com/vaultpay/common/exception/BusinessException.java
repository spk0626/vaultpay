package com.vaultpay.common.exception;

import org.springframework.http.HttpStatus;

/**
 * The single exception type for all expected business rule violations.
 *
 * WHY ONE CUSTOM EXCEPTION instead of many (InsufficientFundsException,
 * WalletNotFoundException, etc.)?
 *
 * Because a single exception with a status code and message is easier to handle
 * uniformly in the GlobalExceptionHandler.
 *
 * RuntimeException → unchecked → callers don't need try/catch.
 * Spring's @Transactional rolls back on RuntimeException by default.
 */

public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(HttpStatus status, String message){
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus(){
        return status;
    }

    /** ── Factory methods keep call sites readable ─────────────────────────────*/

    public static BusinessException notFound(String message) {
        return new BusinessException(HttpStatus.NOT_FOUND, message);
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, message);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(HttpStatus.CONFLICT, message);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(HttpStatus.FORBIDDEN, message);
    }
}
