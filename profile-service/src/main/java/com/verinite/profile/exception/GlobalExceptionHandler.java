package com.verinite.profile.exception;

import com.verinite.common.dto.ApiResponse;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.nio.file.AccessDeniedException;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(EntityNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntime(RuntimeException ex) {
        // Covers "Format not found", "Profile not found", etc.
        String message = ex.getMessage();
        if (message != null && (message.contains("not found") || message.contains("Not found"))) {
            return build(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
        }
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(IllegalStateException ex) {
        return build(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.toList());
        log.warn("Validation failed: {}", details);
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Request validation failed: " + String.join(", ", details));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "Insufficient role for this operation");
    }

    // Catch-all — 500 — NEVER expose raw exception message or stack trace
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled exception in profile-service", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An internal server error occurred. Please contact support.");
    }

    private ResponseEntity<ApiResponse<Void>> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(ApiResponse.error(message != null ? message : "Unknown error", code));
    }
}