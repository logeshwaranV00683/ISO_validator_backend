package com.verinite.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verinite.ai.client.OllamaClient;
import com.verinite.ai.dto.BrdExtractedConfig;
import com.verinite.ai.entity.AiPromptTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs the BRD_PARSE prompt against the document text and parses the
 * JSON response into a BrdExtractedConfig.
 *
 * Never throws — any failure (Ollama unavailable, malformed JSON, etc.)
 * is degraded into a BrdExtractedConfig with empty lists, confidence=0.0,
 * and a warning describing what went wrong.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BrdExtractionService {

    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[\\s\\S]*}");

    private final AiTemplateService aiTemplateService;
    private final OllamaClient      ollamaClient;
    private final ObjectMapper      objectMapper;

    public BrdExtractedConfig extract(String brdText) {
        try {
            AiPromptTemplate template = aiTemplateService.resolveBrdTemplate();
            String prompt = template.getPromptTemplate().replace("{brd_text}", brdText == null ? "" : brdText);

            String rawResponse = ollamaClient.callOllama(prompt);
            if (rawResponse == null || rawResponse.isBlank()) {
                return emptyConfig("Ollama returned an empty response for BRD extraction.");
            }

            String jsonText = extractJsonBlock(rawResponse);
            BrdExtractedConfig config = objectMapper.readValue(jsonText, BrdExtractedConfig.class);

            if (config.getFieldDefinitions() == null) config.setFieldDefinitions(new ArrayList<>());
            if (config.getRules() == null) config.setRules(new ArrayList<>());
            if (config.getWarnings() == null) config.setWarnings(new ArrayList<>());
            if (config.getConfidence() == null) config.setConfidence(0.0);

            return config;
        } catch (Exception e) {
            log.warn("[BRD] Extraction failed, degrading to empty config: {}", e.getMessage());
            return emptyConfig("AI extraction failed: " + e.getMessage());
        }
    }

    /** Ollama sometimes wraps JSON in prose or markdown fences — pull out the {...} block. */
    private String extractJsonBlock(String text) {
        Matcher m = JSON_BLOCK.matcher(text);
        if (m.find()) {
            return m.group();
        }
        return text;
    }

    private BrdExtractedConfig emptyConfig(String warning) {
        return BrdExtractedConfig.builder()
                .switchProfile(BrdExtractedConfig.BrdSwitchProfileDto.builder()
                        .profileName("Untitled BRD Profile")
                        .description("")
                        .environment("DEV")
                        .build())
                .mti(null)
                .fieldDefinitions(new ArrayList<>())
                .rules(new ArrayList<>())
                .confidence(0.0)
                .warnings(Collections.singletonList(warning))
                .build();
    }
}