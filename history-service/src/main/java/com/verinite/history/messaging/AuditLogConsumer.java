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

        log.info("[AuditConsumer] Received | source={} action={} entity={} correlationId={}",
                event.getSourceService(),
                event.getAction(),
                event.getEntityType(),
                event.getCorrelationId());

        try {
            AuditLog auditLog = AuditLog.builder()
                    .userId(event.getUserId())
                    .usernameSnapshot(event.getUsername())       // schema: username_snapshot
                    .userRole(event.getUserRole())
                    .sourceService(event.getSourceService())
                    .action(event.getAction())
                    .entityType(event.getEntityType())
                    .entityId(event.getEntityId() != null       // schema: VARCHAR(50)
                            ? String.valueOf(event.getEntityId()) : null)
                    .entityName(event.getEntityName())
                    .oldValue(event.getBeforeValue())           // schema: old_value
                    .newValue(event.getAfterValue())            // schema: new_value
                    .description(event.getDescription())
                    .ipAddress(event.getIpAddress())
                    .correlationId(event.getCorrelationId())
                    .build();

            auditLogRepository.save(auditLog);

            log.info("[AuditConsumer] Saved | id={} action={} entityType={} entityId={}",
                    auditLog.getId(),
                    auditLog.getAction(),
                    auditLog.getEntityType(),
                    auditLog.getEntityId());

        } catch (Exception e) {
            log.error("[AuditConsumer] Failed to persist | correlationId={} error={}",
                    event.getCorrelationId(), e.getMessage(), e);
        }
    }
}