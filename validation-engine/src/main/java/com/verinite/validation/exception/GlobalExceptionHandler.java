package com.verinite.validation.exception;

import com.verinite.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.toList());
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Request validation failed: " + String.join(", ", details));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "Insufficient role for this operation");
    }

    // RuntimeException covers "Invalid ISO8583 message: ..." from parser failures
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntime(RuntimeException ex) {
        log.warn("RuntimeException in validation-engine: {}", ex.getMessage());
        // Parse/validation errors are 400 — log message is safe (no PAN, already masked)
        String message = ex.getMessage();
        if (message != null && message.startsWith("Invalid ISO8583")) {
            return build(HttpStatus.BAD_REQUEST, "PARSE_ERROR", message);
        }
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    // Catch-all 500 — never expose internal details
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled exception in validation-engine", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An internal server error occurred. Please contact support.");
    }

    private ResponseEntity<ApiResponse<Void>> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(ApiResponse.error(message != null ? message : "Unknown error", code));
    }
}