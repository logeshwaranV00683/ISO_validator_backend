package com.verinite.profile.event;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
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
    private static final String SOURCE_SERVICE = "profile-service";
    private static final String EVENT_TYPE     = "AUDIT_EVENT";

    public void publish(AuditEvent event) {

        var payload = Payload.builder()
                .userId(event.userId())
                .username(event.username())
                .userRole(event.userRole())
                .action(event.action())
                .entityType(event.entityType())
                .entityId(event.entityId())
                .entityName(event.entityName())
                .beforeValue(event.beforeValue())
                .afterValue(event.afterValue())
                .description(event.description())
                .ipAddress(event.ipAddress())
                .correlationId(event.correlationId())
                .build();

        var message = AuditMessage.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(EVENT_TYPE)
                .sourceService(SOURCE_SERVICE)
                .timestamp(Instant.now().toString())
                .payload(payload)
                .build();

        String routingKey = "audit." + event.entityType().toLowerCase()
                + "." + event.action().toLowerCase();

        rabbitTemplate.convertAndSend(AUDIT_EXCHANGE, routingKey, message);
        log.info("[MQ] Audit published — action={} entityType={} entityId={}",
                event.action(), event.entityType(), event.entityId());
    }

    @Builder
    public record AuditEvent(
            Long   userId,
            String username,
            String userRole,
            String action,
            String entityType,
            Long   entityId,
            String entityName,
            String beforeValue,
            String afterValue,
            String description,
            String ipAddress,
            String correlationId
    ) {}

    @Builder
    public record AuditMessage(
            String  eventId,
            String  eventType,
            String  sourceService,
            String  timestamp,
            Payload payload
    ) {}

    @Builder
    public record Payload(
            Long   userId,
            String username,
            String userRole,
            String action,
            String entityType,
            Long   entityId,
            String entityName,
            String beforeValue,
            String afterValue,
            String description,
            String ipAddress,
            String correlationId
    ) {}
}