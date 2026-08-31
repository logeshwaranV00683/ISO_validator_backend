package com.verinite.ai.controller;

import com.verinite.ai.dto.AiExplainRequest;
import com.verinite.ai.dto.AiExplainResponse;
import com.verinite.ai.dto.SuggestSwitchRequest;
import com.verinite.ai.dto.SwitchSuggestionResponse;
import com.verinite.ai.service.OllamaService;
import com.verinite.ai.service.SwitchPredictorService;
import com.verinite.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

/**
 * Internal endpoint — NOT exposed through API Gateway (network-isolated).
 * Called by validation-engine via Feign.
 *
 * RULE: This endpoint MUST NEVER return 5xx.
 * AI failure → return null explanation, validation continues normally.
 */
@RestController
@RequestMapping("/internal/ai")
@RequiredArgsConstructor
@Slf4j
public class InternalAiController {

    private final OllamaService ollamaService;
    private final SwitchPredictorService switchPredictorService;

    @PostMapping("/explain")
    public ResponseEntity<ApiResponse<AiExplainResponse>> explain(
            @RequestBody AiExplainRequest request) {
        try {
            AiExplainResponse explanation = ollamaService.getExplanation(request);
            return ResponseEntity.ok(ApiResponse.success(explanation, "OK"));
        } catch (Exception e) {
            // Safety net — ollamaService already handles all failures internally,
            // but belt-and-suspenders: never let this endpoint return 5xx
            log.warn("[Internal] AI explain unexpected error runRef={}: {}",
                    request.getRunReference(), e.getMessage());
            return ResponseEntity.ok(ApiResponse.success(null, "AI unavailable — skipped"));
        }
    }

    @PostMapping("/suggest-switch")
    public ResponseEntity<ApiResponse<SwitchSuggestionResponse>> suggestSwitch(
            @RequestBody SuggestSwitchRequest request) {
        try {
            SwitchSuggestionResponse response = switchPredictorService.suggest(request.getRawMessage());
            return ResponseEntity.ok(ApiResponse.success(response, "OK"));
        } catch (Exception e) {
            log.warn("[Internal] suggest-switch failed: {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.success(
                    SwitchSuggestionResponse.builder().suggestions(Collections.emptyList()).build(), "No suggestions"));
        }
    }
}