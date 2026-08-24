//package com.verinite.ai.exception;
//
//import com.verinite.common.dto.ApiResponse;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.security.access.AccessDeniedException;
//import org.springframework.web.bind.MethodArgumentNotValidException;
//import org.springframework.web.bind.annotation.ExceptionHandler;
//import org.springframework.web.bind.annotation.RestControllerAdvice;
//
//import java.util.List;
//import java.util.stream.Collectors;
//
//@RestControllerAdvice
//@Slf4j
//public class GlobalExceptionHandler {
//
//    @ExceptionHandler(NotFoundException.class)
//    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException ex) {
//        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
//    }
//
//    @ExceptionHandler(IllegalStateException.class)
//    public ResponseEntity<ApiResponse<Void>> handleConflict(IllegalStateException ex) {
//        return build(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage());
//    }
//
//    @ExceptionHandler(IllegalArgumentException.class)
//    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException ex) {
//        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
//    }
//
//    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
//    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
//            org.springframework.dao.DataIntegrityViolationException ex) {
//        log.warn("Data integrity violation: {}", ex.getMessage());
//        return build(HttpStatus.CONFLICT, "CONFLICT",
//                "This operation conflicts with existing data. Please refresh and try again.");
//    }
//
//    @ExceptionHandler(MethodArgumentNotValidException.class)
//    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
//        List<String> details = ex.getBindingResult().getFieldErrors().stream()
//                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
//                .collect(Collectors.toList());
//        log.warn("Validation failed: {}", details);
//        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
//                "Request validation failed: " + String.join(", ", details));
//    }
//
//    @ExceptionHandler(AccessDeniedException.class)
//    public ResponseEntity<ApiResponse<Void>> handleForbidden(AccessDeniedException ex) {
//        return build(HttpStatus.FORBIDDEN, "FORBIDDEN",
//                "Insufficient role for this operation");
//    }
//
//    // Catch-all 500 — never expose raw exception message or stack trace
//    @ExceptionHandler(Exception.class)
//    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
//        log.error("Unhandled exception in ai-service", ex);
//        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
//                "An internal server error occurred. Please contact support.");
//    }
//
//    private ResponseEntity<ApiResponse<Void>> build(HttpStatus status, String code, String message) {
//        return ResponseEntity.status(status)
//                .body(ApiResponse.error(message != null ? message : "Unknown error", code));
//    }
//}


package com.verinite.ai.exception;

import com.verinite.common.dto.ApiResponse;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    private static final Pattern DOWNSTREAM_MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]*)\"");
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
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
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ApiResponse<Void>> handleFeign(FeignException ex) {
        log.warn("Downstream service call failed: status={} message={}", ex.status(), ex.getMessage());

        String extracted = extractDownstreamMessage(ex.getMessage());
        HttpStatus status = HttpStatus.resolve(ex.status());
        if (status == null || !status.is4xxClientError()) {
            status = HttpStatus.BAD_GATEWAY;
        }
        String code = (ex.status() == 409) ? "CONFLICT" : "DOWNSTREAM_ERROR";
        return build(status, code, extracted != null ? extracted : "A dependent service rejected the request.");
    }

    private String extractDownstreamMessage(String feignMessage) {
        if (feignMessage == null) return null;
        Matcher m = DOWNSTREAM_MESSAGE.matcher(feignMessage);
        return m.find() ? m.group(1) : null;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled exception in ai-service", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An internal server error occurred. Please contact support.");
    }

    private ResponseEntity<ApiResponse<Void>> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(ApiResponse.error(message != null ? message : "Unknown error", code));
    }
}