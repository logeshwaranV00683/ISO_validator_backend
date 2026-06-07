package com.verinite.validation.client;

import com.verinite.common.dto.ApiResponse;
import com.verinite.validation.dto.AiExplainRequestDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Feign fallback — called when ai-service is unreachable or circuit breaker is open.
 * Returns null explanation; validation response is still returned to the client.
 */
@Component
@Slf4j
public class AIServiceClientFallback implements AIServiceClient {

    @Override
    public ApiResponse<String> explain(AiExplainRequestDto request) {
        log.warn("[AI Fallback] ai-service unreachable for runRef={} — aiExplanation=null",
                request != null ? request.getRunReference() : "UNKNOWN");
        return ApiResponse.success(null, "AI service unavailable — skipped");
    }
}