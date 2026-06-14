package com.verinite.ai.service;

import com.verinite.ai.dto.OllamaConfigDto;
import com.verinite.ai.dto.UpdateConfigRequest;
import com.verinite.ai.entity.OllamaConfig;
import com.verinite.ai.event.AiAuditEventPublisher;
import com.verinite.ai.exception.NotFoundException;
import com.verinite.ai.repository.OllamaConfigRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class OllamaConfigService {

    private final OllamaConfigRepository configRepository;
    private final AiAuditEventPublisher  auditPublisher;

    // ═══════════════════════════════════════════════════════════════════════
    // READ
    // ═══════════════════════════════════════════════════════════════════════

    public List<OllamaConfigDto> getAllConfig() {
        return configRepository.findAll().stream()
                .map(this::toDto).toList();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UPDATE — single key
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public OllamaConfigDto updateConfig(UpdateConfigRequest req, String username) {
        OllamaConfig config = configRepository.findByConfigKey(req.getKey())
                .orElseThrow(() -> new NotFoundException("Config key not found: " + req.getKey()));

        applyUpdate(config, req, username);

        return toDto(config);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UPDATE — bulk
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public List<OllamaConfigDto> updateConfigBulk(List<UpdateConfigRequest> requests, String username) {
        List<OllamaConfigDto> updated = new ArrayList<>();

        for (UpdateConfigRequest req : requests) {
            OllamaConfig config = configRepository.findByConfigKey(req.getKey())
                    .orElseThrow(() -> new NotFoundException("Config key not found: " + req.getKey()));

            applyUpdate(config, req, username);

            updated.add(toDto(config));
        }

        return updated;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // INTERNAL HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Applies the update only if the new value differs from the current value.
     * Skips save + audit publish entirely for no-op updates.
     */
    private void applyUpdate(OllamaConfig config, UpdateConfigRequest req, String username) {
        String oldValue = config.getConfigValue();
        String newValue = req.getValue();

        if (Objects.equals(oldValue, newValue)) {
            return; // no-op — value unchanged, skip save & audit
        }

        config.setConfigValue(newValue);
        config.setUpdatedBy(username);
        configRepository.save(config);

        auditPublisher.publishConfigChange(req.getKey(), oldValue, newValue, username);
    }

    private OllamaConfigDto toDto(OllamaConfig c) {
        return OllamaConfigDto.builder()
                .id(c.getId())
                .key(c.getConfigKey())
                .value(Boolean.TRUE.equals(c.getIsSensitive()) ? "****" : c.getConfigValue())
                .configType(c.getConfigType() != null ? c.getConfigType() : null)
                .description(c.getDescription())
                .isSensitive(c.getIsSensitive())
                .updatedBy(c.getUpdatedBy())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}