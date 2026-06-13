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

    @GetMapping("/ai/config")
    public ResponseEntity<ApiResponse<List<OllamaConfigDto>>> getConfig() {
        List<OllamaConfigDto> dtos = configRepository.findAll().stream()
                .map(this::toDto).toList();
        return ResponseEntity.ok(ApiResponse.success(dtos, "OK"));
    }

    /**
     * PUT /ai/config — FE sends full config object with { key, value } (F8-additional / new issue)
     * No key in path — key is inside the request body.
     */
    @PutMapping("/ai/config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OllamaConfigDto>> updateConfig(
            @RequestBody @Valid UpdateConfigRequest req,
            Authentication auth) {

        String username = auth != null ? auth.getName() : "system";
        OllamaConfig config = configRepository.findByConfigKey(req.getKey())
                .orElseThrow(() -> new NotFoundException("Config key not found: " + req.getKey()));
        String oldValue = config.getConfigValue();
        config.setConfigValue(req.getValue());
        config.setUpdatedBy(username);
        configRepository.save(config);
        auditPublisher.publishConfigChange(req.getKey(), oldValue, req.getValue(), username);
        return ResponseEntity.ok(ApiResponse.success(toDto(config), "Config updated"));
    }

    @GetMapping("/ai/models")
    @SuppressWarnings("unchecked")
    public ResponseEntity<ApiResponse<Object>> listModels() {
        try {
            String endpoint = ollamaClient.getEndpoint();
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

    @PostMapping("/ai/test")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testPrompt(
            @RequestBody Map<String, Object> body) {
        try {
            String mti = (String) body.getOrDefault("mti", "0200");
            String sampleErrors = (String) body.getOrDefault("sampleErrors", "DE7 missing");
            Integer profileId = (Integer) body.getOrDefault("profileId", 1);

            String prompt = "You are an ISO 8583 payment message expert.\n" +
                    "A validation was run on MTI " + mti + " for profile ID " + profileId + ".\n" +
                    "The following errors were found:\n" + sampleErrors + "\n\n" +
                    "For each error, explain:\n" +
                    "1. What the field is\n" +
                    "2. Why it is mandatory\n" +
                    "3. How to fix it";

            String result = ollamaClient.callOllama(prompt);

            return ResponseEntity.ok(ApiResponse.success(
                    Map.of("result", result != null ? result : "No response",
                            "model", ollamaClient.getModelName(),
                            "status", "SUCCESS"),
                    "AI test complete"));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.success(
                    Map.of("error", e.getMessage(), "status", "FAILED"), "AI test failed"));
        }
    }

    private OllamaConfigDto toDto(OllamaConfig c) {
        return OllamaConfigDto.builder()
                .id(c.getId())
                .key(c.getConfigKey())             // F9: renamed
                .value(Boolean.TRUE.equals(c.getIsSensitive()) ? "****" : c.getConfigValue())  // F9: renamed
                .configType(c.getConfigType() != null ? c.getConfigType() : null)
                .description(c.getDescription())
                .isSensitive(c.getIsSensitive())
                .updatedBy(c.getUpdatedBy())        // F9: added
                .updatedAt(c.getUpdatedAt())        // F9: added
                .build();
    }
}