package com.verinite.ai.client;

import com.verinite.ai.repository.OllamaConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Low-level HTTP client for Ollama /api/generate.
 *
 * Key: the ollama.endpoint config value ALREADY contains the full URL
 * including /api/generate (e.g. http://localhost:11434/api/generate).
 * Do NOT append /api/generate again.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OllamaClient {

    private final RestTemplate            restTemplate;
    private final OllamaConfigRepository  configRepo;

    /**
     * POST to Ollama /api/generate and return the response text.
     * Throws RuntimeException on HTTP/network failure — caller handles fallback.
     */
    @SuppressWarnings("unchecked")
    public String callOllama(String prompt) {
        // Config: http://localhost:11434/api/generate  ← full URL already
        String endpoint = getConfig("ollama.endpoint");
        String model    = getConfig("ollama.model");

        Map<String, Object> request = new HashMap<>();
        request.put("model",  model);
        request.put("prompt", prompt);
        request.put("stream", false);

        // Respect temperature and max_tokens from DB config
        try {
            String temperature = configRepo.findByConfigKey("ollama.temperature")
                    .map(c -> c.getConfigValue()).orElse("0.3");
            String maxTokens = configRepo.findByConfigKey("ollama.max_tokens")
                    .map(c -> c.getConfigValue()).orElse("1024");
            request.put("options", Map.of(
                    "temperature", Double.parseDouble(temperature),
                    "num_predict", Integer.parseInt(maxTokens)
            ));
        } catch (NumberFormatException e) {
            log.warn("Invalid numeric config for temperature/max_tokens — using defaults");
        }

        log.debug("Calling Ollama endpoint={} model={}", endpoint, model);

        // endpoint is the full URL: http://localhost:11434/api/generate
        Map<String, Object> response = restTemplate.postForObject(
                endpoint, request, Map.class);

        if (response == null) {
            throw new RuntimeException("Ollama returned null response");
        }
        String text = (String) response.get("response");
        log.debug("Ollama response length={}", text != null ? text.length() : 0);
        return text;
    }

    /** Returns the base endpoint stored in config (e.g. http://localhost:11434/api/generate). */
    public String getEndpoint() {
        return configRepo.findByConfigKey("ollama.endpoint")
                .map(c -> c.getConfigValue())
                .orElse("http://localhost:11434/api/generate");
    }

    public String getModelName() {
        return configRepo.findByConfigKey("ollama.model")
                .map(c -> c.getConfigValue())
                .orElse("unknown");
    }

    private String getConfig(String key) {
        return configRepo.findByConfigKey(key)
                .map(c -> c.getConfigValue())
                .orElseThrow(() -> new RuntimeException("Ollama config not found: " + key));
    }
}