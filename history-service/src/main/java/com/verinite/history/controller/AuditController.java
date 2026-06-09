package com.verinite.history.controller;

import com.verinite.history.dto.response.ApiResponse;
import com.verinite.history.dto.response.AuditLogResponse;
import com.verinite.history.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
@Slf4j
public class AuditController {

    private final AuditService auditService;

    /** GET /audit — was GET /audit/logs — /logs subpath removed (F4) */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AuditLogResponse>>> listAuditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long   userId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<AuditLogResponse> result = auditService.listAuditLogs(
                action, entityType, userId, dateFrom, dateTo, page, size);
        return ResponseEntity.ok(ApiResponse.success(result, "Audit logs fetched"));
    }

    /** GET /audit/{auditId} — was GET /audit/logs/{auditId} */
    @GetMapping("/{auditId}")
    public ResponseEntity<ApiResponse<AuditLogResponse>> getById(@PathVariable Long auditId) {
        return ResponseEntity.ok(ApiResponse.success(auditService.getById(auditId), "Audit log found"));
    }
}