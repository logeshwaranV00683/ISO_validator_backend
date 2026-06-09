package com.verinite.history.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verinite.history.dto.event.AuditLogEvent;
import com.verinite.history.entity.AuditLog;
import com.verinite.history.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogConsumer {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper       objectMapper;   // Spring Boot auto-configures this

    @RabbitListener(queues = "history.audit-logs")
    public void consumeAuditEvent(AuditLogEvent event) {

        if (event == null) {
            log.warn("[AuditConsumer] Received null event — skipping");
            return;
        }

        AuditLogEvent.Payload p = event.getPayload();
        if (p == null) {
            log.warn("[AuditConsumer] Event has no payload — source={} eventId={}",
                    event.getSourceService(), event.getEventId());
            return;
        }

        log.info("[AuditConsumer] Received | source={} action={} entity={} correlationId={}",
                event.getSourceService(), p.getAction(), p.getEntityType(),
                p.getCorrelationId() != null ? p.getCorrelationId() : event.getCorrelationId());

        try {
            String oldVal = p.getOldValue()    != null ? p.getOldValue()    : p.getBeforeValue();
            String newVal = p.getNewValue()    != null ? p.getNewValue()    : p.getAfterValue();
            String corrId = p.getCorrelationId() != null
                    ? p.getCorrelationId() : event.getCorrelationId();

            AuditLog auditLog = AuditLog.builder()
                    .userId(p.getUserId())
                    .usernameSnapshot(p.getUsername())
                    .userRole(p.getUserRole())
                    .sourceService(event.getSourceService())
                    .action(p.getAction())
                    .entityType(p.getEntityType())
                    .entityId(p.getEntityId() != null ? String.valueOf(p.getEntityId()) : null)
                    .entityName(p.getEntityName())
                    // FIX: MySQL JSON column rejects plain strings — wrap as JSON
                    .oldValue(toJson(oldVal))
                    .newValue(toJson(newVal))
                    .description(p.getDescription())
                    .ipAddress(p.getIpAddress())
                    .correlationId(corrId)
                    .build();

            auditLogRepository.save(auditLog);

            log.info("[AuditConsumer] Saved | id={} action={} entityType={} entityId={}",
                    auditLog.getId(), auditLog.getAction(),
                    auditLog.getEntityType(), auditLog.getEntityId());

        } catch (Exception e) {
            log.error("[AuditConsumer] Failed to persist | eventId={} error={}",
                    event.getEventId(), e.getMessage(), e);
        }
    }

    /**
     * Ensure the value is valid JSON before writing to a MySQL JSON column.
     * If it's already valid JSON (object, array, or quoted string) → keep as-is.
     * If it's a plain string → wrap as a JSON string literal.
     * If null or blank → return null.
     */
    private String toJson(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim();
        // Already valid JSON?
        if (v.startsWith("{") || v.startsWith("[")) {
            try {
                objectMapper.readTree(v);
                return v;   // valid JSON object or array
            } catch (Exception ignored) { /* fall through to wrap */ }
        }
        if (v.startsWith("\"") && v.endsWith("\"")) {
            try {
                objectMapper.readTree(v);
                return v;   // valid JSON string
            } catch (Exception ignored) { /* fall through to wrap */ }
        }
        // Plain string — serialize as a JSON string literal
        try {
            return objectMapper.writeValueAsString(v);
        } catch (Exception e) {
            log.warn("[AuditConsumer] Could not serialize value as JSON — storing null. value={}", v);
            return null;
        }
    }
}