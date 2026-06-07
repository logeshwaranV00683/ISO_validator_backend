package com.verinite.validation.messaging;

import com.verinite.validation.config.RabbitMQConfig;
import com.verinite.validation.dto.ValidationRunEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes ValidationRunEvent to the validation.events direct exchange.
 * Fire-and-forget — failures are LOGGED but never propagated.
 * The caller (ValidationServiceImpl) returns the response immediately.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ValidationRunPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(ValidationRunEvent event) {
        if (event == null) return;
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.VALIDATION_EVENTS_EXCHANGE,
                    RabbitMQConfig.VALIDATION_RUN_ROUTING_KEY,
                    event);
            log.debug("Published validation run event runRef={} status={}",
                    event.getRunReference(), event.getStatus());
        } catch (Exception e) {
            // MQ down — log and continue; never fail the validation response
            log.warn("Failed to publish MQ event runRef={}: {}",
                    event.getRunReference(), e.getMessage());
        }
    }
}