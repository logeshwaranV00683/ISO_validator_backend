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

    public Page<AuditLogResponse> listAuditLogs(
            String action, String entityType, String entityId, String sourceService, Long userId,
            LocalDate dateFrom, LocalDate dateTo,
            int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Specification<AuditLog> spec =
                AuditLogSpec.filter(action, entityType, entityId, sourceService, userId, dateFrom, dateTo);
        return auditLogRepository.findAll(spec, pageable).map(this::toResponse);

//        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
//        Specification<AuditLog> spec = AuditLogSpec.filter(action, entityType, userId, dateFrom, dateTo);
//        return auditLogRepository.findAll(spec, pageable).map(this::toResponse);
    }

    public AuditLogResponse getById(Long id) {
        AuditLog audit = auditLogRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Audit log not found: " + id));
        return toResponse(audit);
    }

    private AuditLogResponse toResponse(AuditLog a) {
        return AuditLogResponse.builder()
                .auditId(a.getId())          // entity field is id, DTO field is auditId
                .action(a.getAction())
                .entityType(a.getEntityType())
                .entityId(a.getEntityId())
                .entityName(a.getEntityName())
                .userId(a.getUserId())
                .usernameSnapshot(a.getUsernameSnapshot())
                .userRole(a.getUserRole())
                .sourceService(a.getSourceService())
                .description(a.getDescription())
                .correlationId(a.getCorrelationId())
                .ipAddress(a.getIpAddress())
                .oldValue(a.getOldValue())
                .newValue(a.getNewValue())
                .createdAt(a.getCreatedAt())
                .build();
    }
}