package com.verinite.ai.controller;

import com.verinite.ai.client.OllamaClient;
import com.verinite.ai.dto.AiHealthDto;
import com.verinite.ai.dto.OllamaConfigDto;
import com.verinite.ai.dto.UpdateConfigRequest;
import com.verinite.ai.service.OllamaConfigService;
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
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@Slf4j
public class AiConfigController {

    private final OllamaClient        ollamaClient;
    private final OllamaConfigService configService;

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
        return ResponseEntity.ok(ApiResponse.success(configService.getAllConfig(), "OK"));
    }

    /**
     * PUT /ai/config — FE sends single { key, value } object.
     * No-op if value is unchanged (handled in service).
     */
    @PutMapping("/ai/config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OllamaConfigDto>> updateConfig(
            @RequestBody @Valid UpdateConfigRequest req,
            Authentication auth) {

        String username = auth != null ? auth.getName() : "system";
        OllamaConfigDto updated = configService.updateConfig(req, username);
        return ResponseEntity.ok(ApiResponse.success(updated, "Config updated"));
    }

    /**
     * PUT /ai/config/bulk — FE sends an array of { key, value } objects.
     * Each entry that is unchanged from its current value is skipped (no save, no audit).
     */
    @PutMapping("/ai/config/bulk")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<OllamaConfigDto>>> updateConfigBulk(
            @RequestBody @Valid List<UpdateConfigRequest> requests,
            Authentication auth) {

        String username = auth != null ? auth.getName() : "system";
        List<OllamaConfigDto> updated = configService.updateConfigBulk(requests, username);
        return ResponseEntity.ok(ApiResponse.success(updated, "Config updated"));
    }

    @GetMapping("/ai/models")
    @SuppressWarnings("unchecked")
    public ResponseEntity<ApiResponse<Object>> listModels() {
        try {
            String base = ollamaClient.getEndpoint();
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
    public ResponseEntity<ApiResponse<Map<String, Object>>> testPrompt(@RequestBody Map<String, Object> body) {
        try {
            String templateContent   = (String) body.get("templateContent");
            String sampleMti         = (String) body.getOrDefault("sampleMti", "0200");
            String sampleProfileName = (String) body.getOrDefault("sampleProfileName", "Sample Profile");
            Object sampleErrorsRaw   = body.getOrDefault("sampleErrors", List.of());
            Object sampleFieldsRaw   = body.getOrDefault("sampleFields",
                    List.of("DE2 (PAN): 411111******1111", "DE3 (Processing Code): 000000", "DE4 (Amount): 000000010000"));

            if (templateContent == null || templateContent.isBlank()) {
                throw new IllegalArgumentException("templateContent is required to run a test");
            }

            String errorsText = sampleErrorsRaw instanceof List<?> l
                    ? l.stream().map(String::valueOf).collect(Collectors.joining("\n"))
                    : String.valueOf(sampleErrorsRaw);

            String fieldsText = sampleFieldsRaw instanceof List<?> l
                    ? l.stream().map(String::valueOf).collect(Collectors.joining("\n"))
                    : String.valueOf(sampleFieldsRaw);

            String prompt = templateContent
                    .replace("{mti}", sampleMti)
                    .replace("{profile}", sampleProfileName)
                    .replace("{fields}", fieldsText)
                    .replace("{errors}", errorsText);

            long start = System.currentTimeMillis();
            String result = ollamaClient.callOllama(prompt);
            long durationMs = System.currentTimeMillis() - start;

            return ResponseEntity.ok(ApiResponse.success(
                    Map.of("response", result != null && !result.isBlank() ? result : "(Ollama returned an empty response)",
                            "modelUsed", ollamaClient.getModelName(),
                            "durationMs", durationMs,
                            "status", "SUCCESS"),
                    "AI test complete"));
        } catch (Exception e) {
            log.warn("[AI] Test prompt failed: {}", e.getMessage(), e);

            Map<String, Object> failure = new java.util.HashMap<>();
            failure.put("response", null);
            failure.put("modelUsed", ollamaClient.getModelName());
            failure.put("durationMs", 0L);
            failure.put("status", "FAILED");
            failure.put("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            return ResponseEntity.ok(ApiResponse.success(failure, "AI test failed"));
        }
    }
}