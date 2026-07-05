package com.verinite.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verinite.ai.client.OllamaClient;
import com.verinite.ai.dto.SwitchSuggestionResponse;
import com.verinite.ai.entity.BrdEmbeddingChunk;
import com.verinite.ai.repository.BrdEmbeddingChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Suggests a switch profile for a raw ISO 8583 message by semantic similarity
 * against embeddings of previously-confirmed BRD documents.
 * Never throws — returns an empty suggestion list on any failure.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SwitchPredictorService {

    private final BrdEmbeddingChunkRepository chunkRepository;
    private final OllamaClient ollamaClient;
    private final ObjectMapper objectMapper;

    public SwitchSuggestionResponse suggest(String rawMessage) {
        String mtiHint = (rawMessage != null && rawMessage.length() >= 4) ? rawMessage.substring(0, 4) : "";
        try {
            String queryString = "ISO 8583 message MTI=" + mtiHint + " raw="
                    + rawMessage.substring(0, Math.min(rawMessage.length(), 100));

            List<Double> queryEmbedding = ollamaClient.getEmbedding(queryString);
            if (queryEmbedding.isEmpty()) {
                return emptyResponse(mtiHint);
            }

            List<BrdEmbeddingChunk> confirmedChunks = chunkRepository.findByProfileIdIsNotNull();
            if (confirmedChunks.isEmpty()) {
                return emptyResponse(mtiHint);
            }

            // Group by profileId, take max similarity per profile.
            Map<Long, Double> bestScoreByProfile = new HashMap<>();
            for (BrdEmbeddingChunk chunk : confirmedChunks) {
                List<Double> chunkEmbedding = parseEmbedding(chunk.getEmbeddingVector());
                if (chunkEmbedding.isEmpty()) continue;

                double score = cosineSimilarity(queryEmbedding, chunkEmbedding);
                bestScoreByProfile.merge(chunk.getProfileId(), score, Math::max);
            }

            List<SwitchSuggestionResponse.SwitchSuggestion> suggestions = bestScoreByProfile.entrySet().stream()
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .limit(3)
                    .map(entry -> SwitchSuggestionResponse.SwitchSuggestion.builder()
                            .profileId(entry.getKey())
                            .profileName(resolveProfileName(entry.getKey(), confirmedChunks))
                            .confidence(entry.getValue())
                            .reason("BRD document semantic match")
                            .build())
                    .toList();

            return SwitchSuggestionResponse.builder()
                    .suggestions(suggestions)
                    .rawMtiHint(mtiHint)
                    .build();

        } catch (Exception e) {
            log.warn("[BRD] suggestSwitch failed: {}", e.getMessage());
            return emptyResponse(mtiHint);
        }
    }

    /**
     * profileName is not stored on BrdEmbeddingChunk — we don't have a direct
     * lookup table here without another Feign round-trip per suggestion.
     * We fall back to a generic label; the frontend already has full profile
     * data loaded (via getProfiles()) and can resolve the real name from
     * profileId if it wants to display it more richly.
     */
    private String resolveProfileName(Long profileId, List<BrdEmbeddingChunk> chunks) {
        return "Profile #" + profileId;
    }

    @SuppressWarnings("unchecked")
    private List<Double> parseEmbedding(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private double cosineSimilarity(List<Double> a, List<Double> b) {
        if (a.size() != b.size() || a.isEmpty()) return 0.0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        return (normA == 0 || normB == 0) ? 0.0 : dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private SwitchSuggestionResponse emptyResponse(String mtiHint) {
        return SwitchSuggestionResponse.builder()
                .suggestions(Collections.emptyList())
                .rawMtiHint(mtiHint)
                .build();
    }
}