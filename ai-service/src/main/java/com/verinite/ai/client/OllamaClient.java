//package com.verinite.ai.client;
//
//import com.verinite.ai.entity.OllamaConfig;
//import com.verinite.ai.repository.OllamaConfigRepository;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Component;
//import org.springframework.web.client.RestTemplate;
//
//import java.util.Collections;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
///**
// * Low-level HTTP client for Ollama /api/generate.
// *
// * Key: the ollama.endpoint config value ALREADY contains the full URL
// * including /api/generate (e.g. http://localhost:11434/api/generate).
// * Do NOT append /api/generate again.
// */
//@Component
//@RequiredArgsConstructor
//@Slf4j
//public class OllamaClient {
//
//    private final RestTemplate            restTemplate;
//    private final OllamaConfigRepository  configRepo;
//
//    /**
//     * POST to Ollama /api/generate and return the response text.
//     * Throws RuntimeException on HTTP/network failure — caller handles fallback.
//     */
//    @SuppressWarnings("unchecked")
//    public String callOllama(String prompt) {
//        return callOllamaInternal(prompt, false);
//    }
//    public String callOllamaJson(String prompt) {
//        return callOllamaInternal(prompt, true);
//    }
//
//    @SuppressWarnings("unchecked")
//    private String callOllamaInternal(String prompt, boolean jsonMode) {
//        // Config: http://localhost:11434/api/generate  ← full URL already
//        String endpoint = getConfig("ollama.host")+"/api/generate";
//        String model    = getConfig("ollama.model");
//
//        Map<String, Object> request = new HashMap<>();
//        request.put("model",  model);
//        request.put("prompt", prompt);
//        request.put("stream", false);
//        if (jsonMode) {
//            request.put("format", "json");
//        }
//
//        // Respect temperature and max_tokens from DB config
//        try {
//            String temperature = configRepo.findByConfigKey("ollama.temperature")
//                    .map(OllamaConfig::getConfigValue).orElse("0.3");
//            String maxTokens = configRepo.findByConfigKey("ollama.max.tokens")
//                    .map(OllamaConfig::getConfigValue).orElse("1024");
//            request.put("options", Map.of(
//                    "temperature", Double.parseDouble(temperature),
//                    "num_predict", Integer.parseInt(maxTokens)
//            ));
//        } catch (NumberFormatException e) {
//            log.warn("Invalid numeric config for temperature/max_tokens — using defaults");
//        }
//
//        log.debug("Calling Ollama endpoint={} model={}", endpoint, model);
//
//        // endpoint is the full URL: http://localhost:11434/api/generate
//        Map<String, Object> response = restTemplate.postForObject(
//                endpoint, request, Map.class);
//
//        if (response == null) {
//            throw new RuntimeException("Ollama returned null response");
//        }
//        String text = (String) response.get("response");
//        log.debug("Ollama response length={}", text != null ? text.length() : 0);
//        return text;
//    }
//
//    /** Returns the base endpoint stored in config (e.g. http://localhost:11434/api/generate). */
//    public String getEndpoint() {
//        return configRepo.findByConfigKey("ollama.host")
//                .map(OllamaConfig::getConfigValue)
//                .orElse("http://localhost:11434/api/generate");
//    }
//
//    public String getModelName() {
//        return configRepo.findByConfigKey("ollama.model")
//                .map(OllamaConfig::getConfigValue)
//                .orElse("unknown");
//    }
//
//    /**
//     * POST to Ollama /api/embeddings and return the embedding vector.
//     * Used by the BRD ingestion pipeline and switch-suggestion feature.
//     * Never throws — returns an empty list on any failure so callers can
//     * degrade gracefully (BRD ingestion / suggestion must never hard-fail).
//     */
//    @SuppressWarnings("unchecked")
//    public List<Double> getEmbedding(String text) {
//        try {
//            String endpoint = getConfig("ollama.host") + "/api/embeddings";
//            String model    = getConfig("ollama.model");
//            Map<String, Object> request = Map.of("model", model, "prompt", text);
//
//            Map<String, Object> response = restTemplate.postForObject(endpoint, request, Map.class);
//            if (response == null || !response.containsKey("embedding")) {
//                log.warn("Ollama embeddings call returned no 'embedding' field");
//                return Collections.emptyList();
//            }
//            return (List<Double>) response.get("embedding");
//        } catch (Exception e) {
//            log.warn("Ollama getEmbedding failed: {}", e.getMessage());
//            return Collections.emptyList();
//        }
//    }
//
//    private String getConfig(String key) {
//        return configRepo.findByConfigKey(key)
//                .map(OllamaConfig::getConfigValue)
//                .orElseThrow(() -> new RuntimeException("Ollama config not found: " + key));
//    }
//}


package com.verinite.ai.client;

import com.verinite.ai.entity.OllamaConfig;
import com.verinite.ai.repository.OllamaConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
     * Uses the shared ollama.max.tokens config for num_predict.
     * Throws RuntimeException on HTTP/network failure — caller handles fallback.
     */
    public String callOllama(String prompt) {
        return callOllamaInternal(prompt, null, false);
    }

    /**
     * Same as callOllama(String), but lets the caller cap num_predict
     * (max tokens generated) below the shared ollama.max.tokens config.
     * Generation time on CPU scales roughly linearly with token count, so
     * short, conversational use cases (like the chatbot) benefit from a
     * tighter cap than long structured outputs (like BRD extraction).
     */
    public String callOllama(String prompt, int maxTokensOverride) {
        return callOllamaInternal(prompt, maxTokensOverride, false);
    }

    /**
     * Same as callOllama(String), but sets Ollama's format="json" option so
     * the model is constrained to emit valid JSON rather than free text
     * that may wrap the JSON in prose or markdown fences. Used by BRD
     * extraction, which needs a reliably-parseable structured response.
     */
    public String callOllamaJson(String prompt) {
        return callOllamaInternal(prompt, null, true);
    }

    @SuppressWarnings("unchecked")
    private String callOllamaInternal(String prompt, Integer maxTokensOverride, boolean jsonMode) {
        // Config: http://localhost:11434/api/generate  ← full URL already
        String endpoint = getConfig("ollama.host")+"/api/generate";
        String model    = getConfig("ollama.model");

        Map<String, Object> request = new HashMap<>();
        request.put("model",  model);
        request.put("prompt", prompt);
        request.put("stream", false);
        if (jsonMode) {
            request.put("format", "json");
        }

        // Respect temperature and max_tokens from DB config, unless the
        // caller explicitly overrides max_tokens (e.g. the chatbot).
        try {
            String temperature = configRepo.findByConfigKey("ollama.temperature")
                    .map(OllamaConfig::getConfigValue).orElse("0.3");
            int maxTokens = maxTokensOverride != null
                    ? maxTokensOverride
                    : configRepo.findByConfigKey("ollama.max.tokens")
                    .map(OllamaConfig::getConfigValue).map(Integer::parseInt).orElse(1024);
            request.put("options", Map.of(
                    "temperature", Double.parseDouble(temperature),
                    "num_predict", maxTokens
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
        return configRepo.findByConfigKey("ollama.host")
                .map(OllamaConfig::getConfigValue)
                .orElse("http://localhost:11434/api/generate");
    }

    public String getModelName() {
        return configRepo.findByConfigKey("ollama.model")
                .map(OllamaConfig::getConfigValue)
                .orElse("unknown");
    }

    /**
     * POST to Ollama /api/embeddings and return the embedding vector.
     * Used by the BRD ingestion pipeline and switch-suggestion feature.
     * Never throws — returns an empty list on any failure so callers can
     * degrade gracefully (BRD ingestion / suggestion must never hard-fail).
     */
    @SuppressWarnings("unchecked")
    public List<Double> getEmbedding(String text) {
        try {
            String endpoint = getConfig("ollama.host") + "/api/embeddings";
            String model    = getConfig("ollama.model");
            Map<String, Object> request = Map.of("model", model, "prompt", text);

            Map<String, Object> response = restTemplate.postForObject(endpoint, request, Map.class);
            if (response == null || !response.containsKey("embedding")) {
                log.warn("Ollama embeddings call returned no 'embedding' field");
                return Collections.emptyList();
            }
            return (List<Double>) response.get("embedding");
        } catch (Exception e) {
            log.warn("Ollama getEmbedding failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private String getConfig(String key) {
        return configRepo.findByConfigKey(key)
                .map(OllamaConfig::getConfigValue)
                .orElseThrow(() -> new RuntimeException("Ollama config not found: " + key));
    }
}