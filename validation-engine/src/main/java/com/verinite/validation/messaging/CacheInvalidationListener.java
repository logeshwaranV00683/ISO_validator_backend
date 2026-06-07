package com.verinite.validation.messaging;

import com.verinite.validation.dto.CacheInvalidationEvent;
import com.verinite.validation.iso.PackagerCache;
import com.verinite.validation.service.ValidationCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Listens on validation-engine.cache-invalidation queue (fanout from cache.invalidation exchange).
 *
 * RULE_UPDATED / RULE_DELETED     → evict rulesCache (all entries, safe)
 * FORMAT_UPDATED / FORMAT_ROLLED_BACK → evict PackagerCache for formatId
 *                                       + evict profileFormats Spring Cache
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
        if (event == null || event.getEventType() == null) {
            log.warn("Received null or typeless cache invalidation event — ignoring");
            return;
        }

        log.info("Cache invalidation: type={} id={} profileId={}",
                event.getEventType(), event.getId(), event.getProfileId());

        switch (event.getEventType().toUpperCase()) {

            case "RULE_UPDATED", "RULE_DELETED" -> {
                // Evict all rules — Spring Cache doesn't support wildcard key eviction,
                // so we evict everything; it refills from rules-service within 30s
                cacheService.evictAllRules();
                log.info("rulesCache evicted due to {}", event.getEventType());
            }

            case "FORMAT_UPDATED", "FORMAT_ROLLED_BACK" -> {
                if (event.getId() != null) {
                    packagerCache.evict(event.getId());
                } else {
                    packagerCache.evictAll();
                }
                // Also clear the profile format Feign response cache so the
                // next call fetches fresh xmlContent from profile-service
                if (event.getProfileId() != null) {
                    cacheService.evictProfileFormat(event.getProfileId());
                } else {
                    cacheService.evictAllProfileFormats();
                }
                log.info("PackagerCache + profileFormats evicted due to {}", event.getEventType());
            }

            default -> log.warn("Unknown cache invalidation eventType='{}' — ignoring",
                    event.getEventType());
        }
    }
}