package com.verinite.ai.controller;

import com.verinite.ai.client.OllamaClient;
import com.verinite.ai.dto.AiHealthDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class AiConfigController {

    private final OllamaClient ollamaClient;

    @GetMapping("/ai/health")
    public ResponseEntity<AiHealthDto> health() {
        String model    = ollamaClient.getModelName();
        String endpoint = ollamaClient.getEndpoint();
        try {
            ollamaClient.callOllama("ping");
            return ResponseEntity.ok(new AiHealthDto("UP", model, endpoint));
        } catch (Exception e) {
            log.warn("Ollama health check failed: {}", e.getMessage());
            return ResponseEntity.ok(new AiHealthDto("DOWN", model, endpoint));
        }
    }
}