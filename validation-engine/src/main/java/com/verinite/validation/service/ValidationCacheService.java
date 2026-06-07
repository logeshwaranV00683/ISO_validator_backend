package com.verinite.validation.service;

import com.verinite.validation.client.ProfileClient;
import com.verinite.validation.client.RulesClient;
import com.verinite.validation.dto.EffectiveRuleDto;
import com.verinite.validation.dto.ProfileFormatResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ValidationCacheService {

    private final ProfileClient profileClient;
    private final RulesClient   rulesClient;

    // ── Profile format (Caffeine, TTL 30s, max 100) ──────────────────────────

    @Cacheable(value = "profileFormats", key = "#profileId")
    public ProfileFormatResponseDto getProfileFormat(Long profileId) {
        return profileClient.getFormatForProfile(profileId);
    }

    @CacheEvict(value = "profileFormats", allEntries = true)
    public void evictAllProfileFormats() {
        // called by CacheInvalidationListener on FORMAT_UPDATED event
    }

    @CacheEvict(value = "profileFormats", key = "#profileId")
    public void evictProfileFormat(Long profileId) { }

    // ── Rules cache (Caffeine, TTL 30s, max 200) ─────────────────────────────

    @Cacheable(value = "rulesCache", key = "#profileId + '-' + #mti")
    public List<EffectiveRuleDto> getRules(Long profileId, String mti) {
        return rulesClient.getEffectiveRules(profileId, mti);
    }

    @CacheEvict(value = "rulesCache", allEntries = true)
    public void evictAllRules() {
        // When profileId-specific eviction isn't possible with Spring Cache
        // wildcard keys, we evict everything (safe — cache refills in 30s max)
    }
}