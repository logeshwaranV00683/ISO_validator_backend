package com.verinite.validation.messaging;

import com.verinite.validation.dto.CacheInvalidationEvent;
import com.verinite.validation.iso.PackagerCache;
import com.verinite.validation.service.ValidationCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Listens on validation-engine.cache-invalidation queue.
 *
 * CRITICAL FIX: Previous version switched on event.getEventType() which was
 * always "CACHE_INVALIDATION" — matching nothing.  Now correctly reads
 * event.getPayload().getType() for the actual invalidation reason.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CacheInvalidationListener {

    private final PackagerCache          packagerCache;
    private final ValidationCacheService cacheService;

    @RabbitListener(queues = "#{T(com.verinite.validation.config.RabbitMQConfig)"
            + ".VALIDATION_ENGINE_CACHE_QUEUE}")
    public void handleCacheInvalidation(CacheInvalidationEvent event) {

        if (event == null) {
            log.warn("Received null cache invalidation event — ignoring");
            return;
        }

        CacheInvalidationEvent.Payload payload = event.getPayload();
        if (payload == null || payload.getType() == null) {
            log.warn("Cache invalidation event has no payload.type — source={} — ignoring",
                    event.getSourceService());
            return;
        }

        // FIX: switch on payload.type, NOT event.getEventType()
        String type = payload.getType().toUpperCase();
        log.info("Cache invalidation: type={} profileId={} formatId={} mti={}",
                type, payload.getProfileId(), payload.getFormatId(), payload.getMti());

        switch (type) {

            case "RULE_UPDATED", "RULE_DELETED" -> {
                // Evict entire rules cache — Spring Cache doesn't support wildcard key eviction
                // so we flush everything; it refills from rules-service within 30s TTL
                cacheService.evictAllRules();
                log.info("rulesCache evicted due to {}", type);
            }

            case "FORMAT_UPDATED", "FORMAT_ROLLED_BACK" -> {
                if (payload.getFormatId() != null) {
                    packagerCache.evict(payload.getFormatId());
                } else {
                    packagerCache.evictAll();
                }
                if (payload.getProfileId() != null) {
                    cacheService.evictProfileFormat(payload.getProfileId());
                } else {
                    cacheService.evictAllProfileFormats();
                }
                log.info("PackagerCache + profileFormats evicted due to {}", type);
            }

            default -> log.warn("Unknown cache invalidation type='{}' — ignoring", type);
        }
    }
}