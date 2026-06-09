package com.verinite.auth_service.service;

import com.verinite.auth_service.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemConfigService {

    private final SystemConfigRepository configRepository;

    /**
     * Read an integer config value from system_config table.
     * Falls back to {@code defaultValue} if the key doesn't exist or can't be parsed.
     */
    public int getInt(String key, int defaultValue) {
        try {
            return configRepository.findByConfigKey(key)
                    .map(c -> Integer.parseInt(c.getConfigValue().trim()))
                    .orElse(defaultValue);
        } catch (NumberFormatException e) {
            log.warn("[SystemConfig] Key '{}' has non-integer value — using default {}", key, defaultValue);
            return defaultValue;
        } catch (Exception e) {
            log.warn("[SystemConfig] Failed to read key '{}' — using default {}: {}", key, defaultValue, e.getMessage());
            return defaultValue;
        }
    }

    public String getString(String key, String defaultValue) {
        try {
            return configRepository.findByConfigKey(key)
                    .map(c -> c.getConfigValue().trim())
                    .orElse(defaultValue);
        } catch (Exception e) {
            log.warn("[SystemConfig] Failed to read key '{}' — using default: {}", key, e.getMessage());
            return defaultValue;
        }
    }
}