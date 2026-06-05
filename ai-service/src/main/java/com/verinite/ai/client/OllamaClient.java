package com.verinite.ai.client;

import com.verinite.ai.repository.OllamaConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OllamaClient {

    private final RestTemplate restTemplate;
    private final OllamaConfigRepository configRepo;

    public String callOllama(String prompt) {
        String endpoint = getConfig("ollama.endpoint");
        String model    = getConfig("ollama.model");

        Map<String, Object> request = new HashMap<>();
        request.put("model", model);
        request.put("prompt", prompt);
        request.put("stream", false);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(
                endpoint + "/api/generate", request, Map.class);

        return response != null ? (String) response.get("response") : null;
    }

    public String getEndpoint() {
        return getConfig("ollama.endpoint");
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