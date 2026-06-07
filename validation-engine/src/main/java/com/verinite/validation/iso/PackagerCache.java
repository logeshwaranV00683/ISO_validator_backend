package com.verinite.validation.iso;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOPackager;
import org.jpos.iso.packager.GenericPackager;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * Caffeine cache for jPOS ISOPackager instances keyed by formatId.
 *
 * No TTL — entries live until explicitly evicted via MQ FORMAT_UPDATED event.
 * Max 50 entries — one per active profile format.
 */
@Component
@Slf4j
public class PackagerCache {

    private final Cache<Long, ISOPackager> cache =
            Caffeine.newBuilder()
                    .maximumSize(50)
                    .build();

    /**
     * Return cached packager for formatId, or build one from xmlContent on miss.
     *
     * @param formatId   cache key (from profile-service ProfileFormatResponse)
     * @param xmlContent jPOS GenericPackager XML — used only on cache miss
     */
    public ISOPackager get(Long formatId, String xmlContent) {
        return cache.get(formatId, id -> {
            log.info("PackagerCache MISS — loading formatId={}", formatId);
            try {
                byte[] xmlBytes = xmlContent.getBytes(StandardCharsets.UTF_8);
                return new GenericPackager(new ByteArrayInputStream(xmlBytes));
            } catch (Exception e) {
                throw new RuntimeException(
                        "Failed to load GenericPackager for formatId=" + formatId
                                + ": " + e.getMessage(), e);
            }
        });
    }

    /** Evict a single formatId — called on FORMAT_UPDATED event. */
    public void evict(Long formatId) {
        cache.invalidate(formatId);
        log.info("PackagerCache evicted formatId={}", formatId);
    }

    /** Evict all entries — called on FORMAT_ROLLED_BACK or full reset. */
    public void evictAll() {
        cache.invalidateAll();
        log.info("PackagerCache evicted ALL entries");
    }

    public long size() {
        return cache.estimatedSize();
    }
}