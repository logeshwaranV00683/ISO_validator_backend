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
                    .username(event.getUsername())
                    .userRole(event.getUserRole())
                    .sourceService(event.getSourceService())
                    .action(event.getAction())
                    .entityType(event.getEntityType())
                    .entityId(event.getEntityId())
                    .entityName(event.getEntityName())
                    .beforeValue(event.getBeforeValue())
                    .afterValue(event.getAfterValue())
                    .description(event.getDescription())
                    .ipAddress(event.getIpAddress())
                    .correlationId(event.getCorrelationId())
                    .build();

            auditLogRepository.save(auditLog);

            log.info("[AuditConsumer] Saved | auditId={} action={} entityType={} entityId={}",
                    auditLog.getAuditId(),
                    auditLog.getAction(),
                    auditLog.getEntityType(),
                    auditLog.getEntityId());

        } catch (Exception e) {
            log.error("[AuditConsumer] Failed to persist | correlationId={} error={}",
                    event.getCorrelationId(), e.getMessage(), e);
        }
    }
}