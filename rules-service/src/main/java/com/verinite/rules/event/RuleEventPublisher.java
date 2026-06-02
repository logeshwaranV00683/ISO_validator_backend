package com.verinite.rules.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class RuleEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    // Exchange name — must match what Logeshwaran declared
    private static final String EXCHANGE = "cache.invalidation";

    public void publishRuleUpdated(Long profileId, String mti) {
        Map<String, Object> event = Map.of(
                "eventType", "RULE_UPDATED",
                "profileId", profileId,
                "mti", mti
        );
        rabbitTemplate.convertAndSend(EXCHANGE, "rule.updated", event);
        log.info("Published RULE_UPDATED event for profileId={} mti={}", profileId, mti);
    }

    public void publishRuleDeleted(Long profileId, String mti) {
        Map<String, Object> event = Map.of(
                "eventType", "RULE_DELETED",
                "profileId", profileId,
                "mti", mti
        );
        rabbitTemplate.convertAndSend(EXCHANGE, "rule.deleted", event);
        log.info("Published RULE_DELETED event for profileId={} mti={}", profileId, mti);
    }
}