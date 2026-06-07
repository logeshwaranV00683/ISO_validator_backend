package com.verinite.ai.controller;

import com.verinite.ai.dto.AiExplainRequest;
import com.verinite.ai.service.OllamaService;
import com.verinite.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/explain")
    public ResponseEntity<ApiResponse<String>> explain(
            @RequestBody AiExplainRequest request) {
        try {
            String explanation = ollamaService.getExplanation(request);
            return ResponseEntity.ok(ApiResponse.success(explanation, "OK"));
        } catch (Exception e) {
            // Safety net — ollamaService already handles all failures internally,
            // but belt-and-suspenders: never let this endpoint return 5xx
            log.warn("[Internal] AI explain unexpected error runRef={}: {}",
                    request.getRunReference(), e.getMessage());
            return ResponseEntity.ok(ApiResponse.success(null, "AI unavailable — skipped"));
        }
    }
}