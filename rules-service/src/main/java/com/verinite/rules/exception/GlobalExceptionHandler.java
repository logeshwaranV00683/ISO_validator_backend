package com.verinite.rules.exception;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(EntityNotFoundException ex) {
        return error(404, "NOT_FOUND", ex.getMessage(), null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IllegalStateException ex) {
        return error(409, "CONFLICT", ex.getMessage(), null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return error(400, "BAD_REQUEST", ex.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        return error(400, "VALIDATION_FAILED", "Request validation failed", details);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(AccessDeniedException ex) {
        return error(403, "FORBIDDEN", "Insufficient role for this operation", null);
    }

    // FIX: was returning ex.getMessage() in 500 response — internal details must not leak
//    @ExceptionHandler(Exception.class)
//    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
//        log.error("Unhandled exception in rules-service", ex);   // full trace in log only
//        return error(500, "INTERNAL_ERROR",
//                "An internal server error occurred. Please contact support.", null);
//    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        ex.printStackTrace();   // Temporary for debugging
        log.error("Unhandled exception in rules-service", ex);

        return error(500, "INTERNAL_ERROR",
                ex.getClass().getName() + " : " + ex.getMessage(),
                null);
    }


    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {

        return error(
                400,
                "BAD_REQUEST",
                "Invalid value for '" + ex.getName() + "': " + ex.getValue(),
                null
        );
    }


    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParameter(
            MissingServletRequestParameterException ex) {

        return error(
                400,
                "BAD_REQUEST",
                ex.getParameterName() + " is required",
                null
        );
    }
    private ResponseEntity<Map<String, Object>> error(int status, String code,
                                                      String message, List<String> details) {
        Map<String, Object> errorMap = new LinkedHashMap<>();
        errorMap.put("code",    code);
        errorMap.put("message", message != null ? message : "");
        errorMap.put("details", details != null ? details : List.of());

        Map<String, Object> metaMap = new LinkedHashMap<>();
        metaMap.put("timestamp", Instant.now().toString());
        metaMap.put("service",   "rules-service");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("data",    null);   // null is allowed in LinkedHashMap
        body.put("error",   errorMap);
        body.put("meta",    metaMap);

        return ResponseEntity.status(status).body(body);
    }
}