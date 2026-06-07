package com.verinite.ai.controller;

import com.verinite.ai.client.OllamaClient;
import com.verinite.ai.dto.AiHealthDto;
import com.verinite.ai.dto.OllamaConfigDto;
import com.verinite.ai.dto.UpdateConfigRequest;
import com.verinite.ai.entity.OllamaConfig;
import com.verinite.ai.event.AiAuditEventPublisher;
import com.verinite.ai.exception.NotFoundException;
import com.verinite.ai.repository.OllamaConfigRepository;
import com.verinite.ai.service.AiTemplateService;
import com.verinite.ai.service.OllamaService;
import com.verinite.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class AiConfigController {

    private final OllamaClient           ollamaClient;
    private final OllamaConfigRepository configRepository;
    private final AiAuditEventPublisher  auditPublisher;
    private final OllamaService          ollamaService;
    private final AiTemplateService      templateService;

    /** GET /ai/health */
    @GetMapping("/ai/health")
    public ResponseEntity<ApiResponse<AiHealthDto>> health() {
        String model    = ollamaClient.getModelName();
        String endpoint = ollamaClient.getEndpoint();
        try {
            ollamaClient.callOllama("ping");
            return ResponseEntity.ok(ApiResponse.success(
                    new AiHealthDto("UP", model, endpoint), "Ollama is running"));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.success(
                    new AiHealthDto("DOWN", model, endpoint),
                    "Ollama not reachable: " + e.getMessage()));
        }
    }

    /** GET /ai/config */
    @GetMapping("/ai/config")
    public ResponseEntity<ApiResponse<List<OllamaConfigDto>>> getConfig() {
        List<OllamaConfigDto> dtos = configRepository.findAll().stream()
                .map(this::toDto).toList();
        return ResponseEntity.ok(ApiResponse.success(dtos, "OK"));
    }

    /** PUT /ai/config/{key} */
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
        auditPublisher.publishConfigChange(key, oldValue, req.getValue(), username);
        return ResponseEntity.ok(ApiResponse.success(toDto(config), "Config updated"));
    }

    /**
     * GET /ai/models — list available Ollama models (proxied from Ollama /api/tags)
     */
    @GetMapping("/ai/models")
    @SuppressWarnings("unchecked")
    public ResponseEntity<ApiResponse<Object>> listModels() {
        try {
            String endpoint = ollamaClient.getEndpoint();
            // Ollama /api/generate endpoint → derive /api/tags base
            String base = endpoint.replace("/api/generate", "");
            org.springframework.web.client.RestTemplate rt =
                    new org.springframework.web.client.RestTemplate();
            Map<String, Object> response = rt.getForObject(base + "/api/tags", Map.class);
            return ResponseEntity.ok(ApiResponse.success(response, "Models fetched"));
        } catch (Exception e) {
            log.warn("[AI] Could not list models: {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.success(
                    Map.of("error", "Could not reach Ollama: " + e.getMessage()), "Failed"));
        }
    }

    /**
     * POST /ai/test — test prompt with sample data (ANALYST or ADMIN)
     * Body: { "profileId": 1, "mti": "0200", "sampleErrors": [...] }
     */
    @PostMapping("/ai/test")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testPrompt(
            @RequestBody Map<String, Object> body) {
        try {
            Long   profileId = body.get("profileId") != null
                    ? Long.valueOf(body.get("profileId").toString()) : null;
            String mti       = (String) body.getOrDefault("mti", "0200");
            String testInput = body.getOrDefault("sampleErrors", "Test prompt").toString();

            String result = ollamaClient.callOllama(
                    "This is a test. Respond with: 'AI service is working correctly for " + mti + "'");

            return ResponseEntity.ok(ApiResponse.success(
                    Map.of("result", result != null ? result : "No response",
                            "model", ollamaClient.getModelName(),
                            "status", "SUCCESS"),
                    "AI test complete"));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.success(
                    Map.of("error", e.getMessage(), "status", "FAILED"),
                    "AI test failed"));
        }
    }

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