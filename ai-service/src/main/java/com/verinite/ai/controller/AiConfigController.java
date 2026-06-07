package com.verinite.ai.controller;

import com.verinite.ai.client.OllamaClient;
import com.verinite.ai.dto.AiHealthDto;
import com.verinite.ai.dto.OllamaConfigDto;
import com.verinite.ai.dto.UpdateConfigRequest;
import com.verinite.ai.entity.OllamaConfig;
import com.verinite.ai.event.AiAuditEventPublisher;
import com.verinite.ai.exception.NotFoundException;
import com.verinite.ai.repository.OllamaConfigRepository;
import com.verinite.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * GET  /ai/health     — Ollama UP/DOWN + model name (any role)
 * GET  /ai/config     — All ollama_config rows; sensitive values masked (any role)
 * PUT  /ai/config/{key} — Update a config value (ADMIN only) + audit event
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class AiConfigController {

    private final OllamaClient          ollamaClient;
    private final OllamaConfigRepository configRepository;
    private final AiAuditEventPublisher  auditPublisher;

    // ── GET /ai/health ──────────────────────────────────────────────────────

    /**
     * Pings Ollama and returns UP/DOWN + the currently configured model name.
     */
    @GetMapping("/ai/health")
    public ResponseEntity<ApiResponse<AiHealthDto>> health() {
        String model    = ollamaClient.getModelName();
        String endpoint = ollamaClient.getEndpoint();
        try {
            ollamaClient.callOllama("ping");
            log.debug("[AI] Health check: UP");
            return ResponseEntity.ok(
                    ApiResponse.success(new AiHealthDto("UP", model, endpoint),
                            "Ollama is running"));
        } catch (Exception e) {
            log.warn("[AI] Health check: DOWN — {}", e.getMessage());
            return ResponseEntity.ok(
                    ApiResponse.success(new AiHealthDto("DOWN", model, endpoint),
                            "Ollama not reachable: " + e.getMessage()));
        }
    }

    // ── GET /ai/config ──────────────────────────────────────────────────────

    /**
     * Returns all ollama_config rows.
     * Sensitive entries (isSensitive=true) have their configValue masked as "****".
     */
    @GetMapping("/ai/config")
    public ResponseEntity<ApiResponse<List<OllamaConfigDto>>> getConfig() {
        List<OllamaConfigDto> dtos = configRepository.findAll().stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(dtos, "OK"));
    }

    // ── PUT /ai/config/{key} ────────────────────────────────────────────────

    /**
     * Update a single ollama_config value by key.
     * ADMIN role required.
     * Publishes audit.ai.config-change to audit.events exchange.
     */
    @PutMapping("/ai/config/{key}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OllamaConfigDto>> updateConfig(
            @PathVariable String key,
            @RequestBody @Valid UpdateConfigRequest req,
            Authentication auth) {

        String username = auth != null ? auth.getName() : "system";

        OllamaConfig config = configRepository.findByConfigKey(key)
                .orElseThrow(() -> new NotFoundException("Config key not found: " + key));

        String oldValue = config.getConfigValue();
        config.setConfigValue(req.getValue());
        config.setUpdatedBy(username);
        configRepository.save(config);

        // Publish audit event to audit.events exchange
        auditPublisher.publishConfigChange(key, oldValue, req.getValue(), username);

        log.info("[Config] Updated key={} by {}", key, username);
        return ResponseEntity.ok(ApiResponse.success(toDto(config), "Config updated"));
    }

    // ── private helper ──────────────────────────────────────────────────────

    private OllamaConfigDto toDto(OllamaConfig c) {
        return OllamaConfigDto.builder()
                .id(c.getId())
                .configKey(c.getConfigKey())
                .configValue(Boolean.TRUE.equals(c.getIsSensitive()) ? "****" : c.getConfigValue())
                .configType(c.getConfigType() != null ? c.getConfigType().name() : null)
                .description(c.getDescription())
                .isSensitive(c.getIsSensitive())
                .build();
    }
}