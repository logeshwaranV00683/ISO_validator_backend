package com.verinite.history.messaging;

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

    @RabbitListener(queues = "history.audit-logs")
    public void consumeAuditEvent(AuditLogEvent event) {

        if (event == null) {
            log.warn("[AuditConsumer] Received null event — skipping");
            return;
        }

        // FIX: extract from nested payload; previous code read null top-level fields
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
            // Normalise before/after value field names — different publishers use different names
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
                    .oldValue(oldVal)
                    .newValue(newVal)
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
}