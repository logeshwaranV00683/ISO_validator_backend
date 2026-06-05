package com.verinite.history.service;

import com.verinite.history.dto.response.AuditLogResponse;
import com.verinite.history.entity.AuditLog;
import com.verinite.history.repository.AuditLogRepository;
import com.verinite.history.spec.AuditLogSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    // GET /api/v1/audit/logs
    // Supports: action, entityType, userId, dateFrom, dateTo
    public Page<AuditLogResponse> listAuditLogs(
            String action, String entityType, Long userId,
            LocalDate dateFrom, LocalDate dateTo,
            int page, int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Specification<AuditLog> spec = AuditLogSpec.filter(
                action, entityType, userId, dateFrom, dateTo);

        return auditLogRepository.findAll(spec, pageable).map(this::toResponse);
    }

    // GET /api/v1/audit/logs/{auditId}
    public AuditLogResponse getById(Long auditId) {
        AuditLog audit = auditLogRepository.findById(auditId)
                .orElseThrow(() -> new RuntimeException("Audit log not found: " + auditId));
        return toResponse(audit);
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private AuditLogResponse toResponse(AuditLog a) {
        return AuditLogResponse.builder()
                .auditId(a.getAuditId())
                .userId(a.getUserId())
                .username(a.getUsername())
                .userRole(a.getUserRole())
                .sourceService(a.getSourceService())
                .action(a.getAction())
                .entityType(a.getEntityType())
                .entityId(a.getEntityId())
                .entityName(a.getEntityName())
                .beforeValue(a.getBeforeValue())
                .afterValue(a.getAfterValue())
                .description(a.getDescription())
                .ipAddress(a.getIpAddress())
                .correlationId(a.getCorrelationId())
                .createdAt(a.getCreatedAt())
                .build();
    }
}