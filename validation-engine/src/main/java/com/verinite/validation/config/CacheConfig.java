package com.verinite.validation.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Two separate Caffeine caches:
 *  - profileFormats : Feign responses from profile-service, TTL 30s, max 100
 *  - rulesCache     : Feign responses from rules-service,   TTL 30s, max 200
 *
 * PackagerCache (ISOPackager objects) is managed separately with NO TTL
 * in com.verinite.validation.iso.PackagerCache — it is invalidated only
 * by FORMAT_UPDATED MQ events.
 */
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                caffeineCache("profileFormats", 100, 30),
                caffeineCache("rulesCache",     200, 30)
        ));
        return manager;
    }

    private CaffeineCache caffeineCache(String name, int maxSize, int ttlSeconds) {
        com.github.benmanes.caffeine.cache.Cache<Object, Object> cache =
                Caffeine.newBuilder()
                        .maximumSize(maxSize)
                        .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
                        .build();
        return new CaffeineCache(name, cache);
    }
}