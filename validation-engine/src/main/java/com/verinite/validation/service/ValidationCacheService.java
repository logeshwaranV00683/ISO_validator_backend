package com.verinite.validation.service;

import com.verinite.validation.client.ProfileClient;
import com.verinite.validation.client.RulesClient;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ValidationCacheService {

    private final ProfileClient profileClient;
    private final RulesClient rulesClient;

    @Cacheable(value = "profileFormats", key = "#profileId")
    public Map<String, Object> getProfileFormat(Long profileId) {
        return profileClient.getFormatForProfile(profileId);
    }

    @Cacheable(value = "rulesCache", key = "#profileId + '-' + #mti")
    public List<Map<String, Object>> getRules(Long profileId, String mti) {
        return rulesClient.getEffectiveRules(profileId, mti);
    }

    @CacheEvict(value = {"profileFormats", "rulesCache"}, allEntries = true)
    public void evictAll() {
        // MQ cache.invalidation event வரும்போது இதை call பண்ண
    }
}