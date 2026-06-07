package com.verinite.auth_service.event;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    private static final String AUDIT_EXCHANGE = "audit.events";

    public void publish(String action, String entityType, Long entityId, String entityName,
                        Long userId, String username, String userRole,
                        String beforeValue, String afterValue,
                        String description, String ipAddress, String correlationId) {

        AuditPayload payload = new AuditPayload(
                userId, username, userRole, action, entityType,
                entityId, entityName, beforeValue, afterValue,
                description, ipAddress, correlationId);

        AuditMessage message = new AuditMessage(
                UUID.randomUUID().toString(), "AUDIT_EVENT",
                "auth-service", Instant.now().toString(), payload);

        String routingKey = "audit." + entityType.toLowerCase() + "." + action.toLowerCase();
        rabbitTemplate.convertAndSend(AUDIT_EXCHANGE, routingKey, message);

        log.info("[MQ] Audit published — action={} entityType={}", action, entityType);
    }

    // ── Inner records (no common-lib dependency needed at runtime for MQ payload) ──

    public record AuditMessage(
            String eventId, String eventType,
            String sourceService, String timestamp,
            AuditPayload payload) {}

    public record AuditPayload(
            Long userId, String username, String userRole,
            String action, String entityType, Long entityId, String entityName,
            String beforeValue, String afterValue,
            String description, String ipAddress, String correlationId) {}
}