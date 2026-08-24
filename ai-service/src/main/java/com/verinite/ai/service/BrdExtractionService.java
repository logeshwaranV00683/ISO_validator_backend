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
            String rawResponse = ollamaClient.callOllamaJson(prompt);
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

//package com.verinite.ai.service;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.verinite.ai.client.OllamaClient;
//import com.verinite.ai.dto.BrdExtractedConfig;
//import com.verinite.ai.entity.AiPromptTemplate;
//import com.verinite.ai.util.TextChunker;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.LinkedHashMap;
//import java.util.LinkedHashSet;
//import java.util.List;
//import java.util.Map;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//
///**
// * Runs the BRD_PARSE prompt against the document text and parses the
// * JSON response into a BrdExtractedConfig.
// *
// * Never throws - any failure (Ollama unavailable, malformed JSON, etc.)
// * is degraded into a BrdExtractedConfig with empty lists, confidence=0.0,
// * and a warning describing what went wrong.
// *
// * Two extraction paths:
// *  - Small/typical BRDs (<= MAX_SINGLE_CALL_CHARS): one call, whole document - same
// *    behavior as before, no overhead added.
// *  - Large BRDs: split into a handful of much-larger-than-embedding-sized chunks (see
// *    EXTRACTION_CHUNK_SIZE) and run BRD_PARSE once per chunk, then merge the results.
// *    Each individual call has a far smaller prompt, so prefill + generation time per
// *    call drops substantially - this is the main lever for cutting wall-clock time on
// *    large documents on CPU-only hardware.
// */
//@Service
//@RequiredArgsConstructor
//@Slf4j
//public class BrdExtractionService {
//
//    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[\\s\\S]*}");
//
//    /** Documents at or under this size run as a single call - chunking would only add overhead. */
//    private static final int MAX_SINGLE_CALL_CHARS = 6000;
//
//    /**
//     * Chunk size used ONLY for extraction (deliberately much larger than
//     * BrdIngestService's 800-char embedding chunks). Embedding chunks are sized for
//     * semantic-search granularity; extraction chunks are sized to minimize the number
//     * of LLM calls while keeping each prompt small enough to be fast - a handful of
//     * chunks for a large BRD, not hundreds.
//     */
//    private static final int EXTRACTION_CHUNK_SIZE = 6000;
//
//    private final AiTemplateService aiTemplateService;
//    private final OllamaClient      ollamaClient;
//    private final ObjectMapper      objectMapper;
//
//    public BrdExtractedConfig extract(String brdText) {
//        if (brdText == null || brdText.isBlank()) {
//            return emptyConfig("BRD document text is empty - nothing to extract.");
//        }
//        try {
//            AiPromptTemplate template = aiTemplateService.resolveBrdTemplate();
//
//            if (brdText.length() <= MAX_SINGLE_CALL_CHARS) {
//                return extractSingleShot(template, brdText);
//            }
//            return extractChunked(template, brdText);
//        } catch (Exception e) {
//            log.warn("[BRD] Extraction failed, degrading to empty config: {}", e.getMessage());
//            return emptyConfig("AI extraction failed: " + e.getMessage());
//        }
//    }
//
//    private BrdExtractedConfig extractSingleShot(AiPromptTemplate template, String brdText) throws Exception {
//        String prompt = template.getPromptTemplate().replace("{brd_text}", brdText);
//        String rawResponse = ollamaClient.callOllama(prompt, true); // jsonMode - see OllamaClient
//        return parseResponse(rawResponse, "single-shot extraction");
//    }
//
//    /**
//     * Map-reduce extraction for large BRDs. Reuses the same paragraph-aware chunker as
//     * the embedding pipeline (TextChunker), just with a far larger chunk size, so a
//     * typical large BRD becomes a handful of chunks rather than hundreds. Each chunk is
//     * extracted independently and never fails the whole document - a failed chunk is
//     * skipped and recorded as a warning, and results from the remaining chunks are still
//     * returned.
//     */
//    private BrdExtractedConfig extractChunked(AiPromptTemplate template, String brdText) {
//        List<String> chunks = TextChunker.chunk(brdText, EXTRACTION_CHUNK_SIZE);
//        log.info("[BRD] Document is {} chars - running chunked extraction across {} chunk(s)",
//                brdText.length(), chunks.size());
//
//        List<BrdExtractedConfig> results = new ArrayList<>();
//        for (int i = 0; i < chunks.size(); i++) {
//            String context = "chunk " + (i + 1) + "/" + chunks.size();
//            try {
//                String prompt = template.getPromptTemplate().replace("{brd_text}", chunks.get(i));
//                String rawResponse = ollamaClient.callOllama(prompt, true);
//                results.add(parseResponse(rawResponse, context));
//            } catch (Exception e) {
//                log.warn("[BRD] Extraction failed for {}, skipping: {}", context, e.getMessage());
//                // Skip this chunk rather than failing the whole document - partial
//                // results from the other chunks are still useful.
//            }
//        }
//
//        if (results.isEmpty()) {
//            return emptyConfig("AI extraction failed for every chunk of this document.");
//        }
//        return mergeResults(results, chunks.size());
//    }
//
//    private BrdExtractedConfig parseResponse(String rawResponse, String context) throws Exception {
//        if (rawResponse == null || rawResponse.isBlank()) {
//            throw new RuntimeException("Ollama returned an empty response (" + context + ")");
//        }
//        String jsonText = extractJsonBlock(rawResponse);
//        BrdExtractedConfig config = objectMapper.readValue(jsonText, BrdExtractedConfig.class);
//
//        if (config.getFieldDefinitions() == null) config.setFieldDefinitions(new ArrayList<>());
//        if (config.getRules() == null) config.setRules(new ArrayList<>());
//        if (config.getWarnings() == null) config.setWarnings(new ArrayList<>());
//        if (config.getConfidence() == null) config.setConfidence(0.0);
//        return config;
//    }
//
//    /**
//     * Merges per-chunk results into a single config:
//     *  - fieldDefinitions: de-duplicated by deNumber, keeping whichever chunk's version
//     *    has more populated fields (a chunk that only glances at a DE in passing may
//     *    return a sparser definition than the chunk containing its full spec).
//     *  - rules: de-duplicated by exact match only - distinct rules for the same DE
//     *    (e.g. a length check and a mandatory check) are both kept.
//     *  - switchProfile / mti: document-level facts, usually stated once near the top of
//     *    the BRD rather than repeated per chunk - taken from the first chunk that
//     *    actually found a non-blank value.
//     *  - confidence: averaged across the chunks that produced a result.
//     *  - warnings: unioned, plus a note if any chunks were skipped entirely.
//     */
//    private BrdExtractedConfig mergeResults(List<BrdExtractedConfig> results, int totalChunks) {
//        Map<String, BrdExtractedConfig.BrdFieldDefinitionDto> mergedFields = new LinkedHashMap<>();
//        LinkedHashSet<BrdExtractedConfig.BrdRuleDto> mergedRules = new LinkedHashSet<>();
//        LinkedHashSet<String> mergedWarnings = new LinkedHashSet<>();
//        BrdExtractedConfig.BrdSwitchProfileDto profile = null;
//        String mti = null;
//        double confidenceSum = 0;
//
//        for (BrdExtractedConfig r : results) {
//            for (BrdExtractedConfig.BrdFieldDefinitionDto def : r.getFieldDefinitions()) {
//                if (def == null || def.getDeNumber() == null || def.getDeNumber().isBlank()) continue;
//                String key = def.getDeNumber().trim();
//                BrdExtractedConfig.BrdFieldDefinitionDto existing = mergedFields.get(key);
//                if (existing == null || fieldCompleteness(def) > fieldCompleteness(existing)) {
//                    mergedFields.put(key, def);
//                }
//            }
//            mergedRules.addAll(r.getRules());
//            mergedWarnings.addAll(r.getWarnings());
//            if (profile == null && r.getSwitchProfile() != null
//                    && notBlank(r.getSwitchProfile().getProfileName())) {
//                profile = r.getSwitchProfile();
//            }
//            if (mti == null && notBlank(r.getMti())) {
//                mti = r.getMti();
//            }
//            confidenceSum += r.getConfidence() != null ? r.getConfidence() : 0.0;
//        }
//
//        if (results.size() < totalChunks) {
//            mergedWarnings.add((totalChunks - results.size()) + " of " + totalChunks
//                    + " document chunk(s) failed to extract and were skipped.");
//        }
//
//        return BrdExtractedConfig.builder()
//                .switchProfile(profile != null ? profile : BrdExtractedConfig.BrdSwitchProfileDto.builder()
//                        .profileName("Untitled BRD Profile")
//                        .description("")
//                        .environment("DEV")
//                        .build())
//                .mti(mti)
//                .fieldDefinitions(new ArrayList<>(mergedFields.values()))
//                .rules(new ArrayList<>(mergedRules))
//                .confidence(confidenceSum / results.size())
//                .warnings(new ArrayList<>(mergedWarnings))
//                .build();
//    }
//
//    private int fieldCompleteness(BrdExtractedConfig.BrdFieldDefinitionDto d) {
//        int score = 0;
//        if (notBlank(d.getFieldName())) score++;
//        if (notBlank(d.getDataType())) score++;
//        if (d.getMaxLength() != null) score++;
//        if (d.getIsMandatory() != null) score++;
//        if (d.getIsLlvar() != null) score++;
//        if (d.getIsLllvar() != null) score++;
//        return score;
//    }
//
//    private boolean notBlank(String s) {
//        return s != null && !s.isBlank();
//    }
//
//    /** Ollama sometimes wraps JSON in prose or markdown fences - pull out the {...} block. */
//    private String extractJsonBlock(String text) {
//        Matcher m = JSON_BLOCK.matcher(text);
//        if (m.find()) {
//            return m.group();
//        }
//        return text;
//    }
//
//    private BrdExtractedConfig emptyConfig(String warning) {
//        return BrdExtractedConfig.builder()
//                .switchProfile(BrdExtractedConfig.BrdSwitchProfileDto.builder()
//                        .profileName("Untitled BRD Profile")
//                        .description("")
//                        .environment("DEV")
//                        .build())
//                .mti(null)
//                .fieldDefinitions(new ArrayList<>())
//                .rules(new ArrayList<>())
//                .confidence(0.0)
//                .warnings(Collections.singletonList(warning))
//                .build();
//    }
//}