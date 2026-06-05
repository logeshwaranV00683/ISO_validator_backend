package com.verinite.profile.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FormatEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    private static final String CACHE_EXCHANGE = "cache.invalidation";

    public void publishFormatUpdated(Long profileId, Long formatId) {
        CacheInvalidationEvent event = CacheInvalidationEvent.builder()
                .payload(CacheInvalidationEvent.Payload.builder()
                        .type("FORMAT_UPDATED")
                        .profileId(profileId)
                        .formatId(formatId)
                        .build())
                .build();
        rabbitTemplate.convertAndSend(CACHE_EXCHANGE, "", event);
        log.info("[MQ] FORMAT_UPDATED published — profileId={} formatId={}", profileId, formatId);
    }

    public void publishFormatRolledBack(Long profileId, Long formatId) {
        CacheInvalidationEvent event = CacheInvalidationEvent.builder()
                .payload(CacheInvalidationEvent.Payload.builder()
                        .type("FORMAT_ROLLED_BACK")
                        .profileId(profileId)
                        .formatId(formatId)
                        .build())
                .build();
        rabbitTemplate.convertAndSend(CACHE_EXCHANGE, "", event);
        log.info("[MQ] FORMAT_ROLLED_BACK published — profileId={} formatId={}", profileId, formatId);
    }
}