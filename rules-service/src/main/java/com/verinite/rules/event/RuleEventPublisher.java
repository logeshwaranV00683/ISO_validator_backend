package com.verinite.rules.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RuleEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    private static final String CACHE_EXCHANGE = "cache.invalidation";
    private static final String AUDIT_EXCHANGE  = "audit.events";

    // ── Cache Invalidation ────────────────────────────────────────────────

    public void publishRuleUpdated(Long profileId, String mti) {
        CacheInvalidationEvent event = CacheInvalidationEvent.builder()
                .payload(CacheInvalidationEvent.Payload.builder()
                        .type("RULE_UPDATED")
                        .profileId(profileId)
                        .mti(mti)
                        .build())
                .build();
        // Fanout exchange — routing key ignored; empty string is idiomatic
        rabbitTemplate.convertAndSend(CACHE_EXCHANGE, "", event);
        log.info("[MQ] RULE_UPDATED published — profileId={} mti={}", profileId, mti);
    }

    public void publishRuleDeleted(Long profileId, String mti) {
        CacheInvalidationEvent event = CacheInvalidationEvent.builder()
                .payload(CacheInvalidationEvent.Payload.builder()
                        .type("RULE_DELETED")
                        .profileId(profileId)
                        .mti(mti)
                        .build())
                .build();
        rabbitTemplate.convertAndSend(CACHE_EXCHANGE, "", event);
        log.info("[MQ] RULE_DELETED published — profileId={} mti={}", profileId, mti);
    }

    // ── Audit Events ─────────────────────────────────────────────────────

    public void publishAudit(AuditEvent event) {
        // Topic routing key: audit.rule.create / audit.rule.update / audit.field_definition.delete …
        String entityPart = event.getPayload().getEntityType().toLowerCase().replace("_", ".");
        String actionPart = event.getPayload().getAction().toLowerCase();
        String routingKey = "audit." + entityPart + "." + actionPart;

        rabbitTemplate.convertAndSend(AUDIT_EXCHANGE, routingKey, event);
        log.info("[MQ] Audit published — action={} entityType={} entityId={}",
                event.getPayload().getAction(),
                event.getPayload().getEntityType(),
                event.getPayload().getEntityId());
    }
}