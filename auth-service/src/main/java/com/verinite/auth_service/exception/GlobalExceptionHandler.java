package com.verinite.auth_service.exception;

import com.verinite.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(IllegalStateException ex) {
        // Covers "Account locked", "Invalid credentials" flows
        return build(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("Validation failed");
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", msg);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "Insufficient role for this operation");
    }

    // FIX: was missing — any unhandled RuntimeException was falling through
    // and potentially leaking internal state. Now returns 400 for business errors.
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntime(RuntimeException ex) {
        String message = ex.getMessage();
        log.warn("RuntimeException in auth-service: {}", message);
        // Account locked / invalid credentials are surfaced as BAD_REQUEST, not 500
        if (message != null && (
                message.contains("Invalid credentials") ||
                        message.contains("Account locked") ||
                        message.contains("not found") ||
                        message.contains("already exists"))) {
            return build(HttpStatus.BAD_REQUEST, "AUTH_ERROR", message);
        }
        // FIX: don't expose unknown internal runtime message — treat as 500
        log.error("Unexpected RuntimeException in auth-service", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An internal server error occurred. Please contact support.");
    }

    // FIX: catch-all 500 — was completely missing; stack traces could leak
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled exception in auth-service", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An internal server error occurred. Please contact support.");
    }

    private ResponseEntity<ApiResponse<Void>> build(HttpStatus status, String code, String msg) {
        return ResponseEntity.status(status)
                .body(ApiResponse.error(msg != null ? msg : "Unknown error", code));
    }
}