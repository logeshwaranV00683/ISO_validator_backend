package com.verinite.ai.event;

import com.verinite.ai.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiAuditEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * Publishes audit.ai.config-change to audit.events exchange.
     * Consumed by history-service for the audit_logs table.
     */
    public void publishConfigChange(String configKey,
                                    String oldValue,
                                    String newValue,
                                    String username) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId",       UUID.randomUUID().toString());
        payload.put("eventType",     "AUDIT_EVENT");
        payload.put("sourceService", "ai-service");
        payload.put("timestamp",     Instant.now().toString());
        payload.put("payload", Map.of(
                "username",   username != null ? username : "system",
                "action",     "UPDATE",
                "entityType", "AI_CONFIG",
                "entityName", configKey,
                "beforeValue", oldValue  != null ? oldValue : "",
                "afterValue",  newValue  != null ? newValue : "",
                "description", "Ollama config updated: " + configKey
        ));

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.AUDIT_EVENTS_EXCHANGE,
                RabbitMQConfig.AUDIT_AI_CONFIG_ROUTING_KEY,
                payload);

        log.info("[MQ] audit.ai.config-change published — key={}", configKey);
    }

    /**
     * Publishes audit.ai.prompt-change to audit.events exchange.
     */
    public void publishPromptChange(Long templateId,
                                    String action,
                                    String username) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId",       UUID.randomUUID().toString());
        payload.put("eventType",     "AUDIT_EVENT");
        payload.put("sourceService", "ai-service");
        payload.put("timestamp",     Instant.now().toString());
        payload.put("payload", Map.of(
                "username",   username != null ? username : "system",
                "action",     action,
                "entityType", "AI_PROMPT",
                "entityId",   templateId,
                "description", "AI prompt template " + action.toLowerCase() + ": id=" + templateId
        ));

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.AUDIT_EVENTS_EXCHANGE,
                RabbitMQConfig.AUDIT_AI_PROMPT_ROUTING_KEY,
                payload);

        log.info("[MQ] audit.ai.prompt-change published — templateId={} action={}", templateId, action);
    }
}