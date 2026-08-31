package com.verinite.validation.client;

import com.verinite.common.dto.ApiResponse;
import com.verinite.validation.dto.AiExplainRequestDto;
import com.verinite.validation.dto.AiExplainResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AIServiceClientFallback implements AIServiceClient {

    @Override
    public ApiResponse<AiExplainResponseDto> explain(AiExplainRequestDto request) {
        log.warn("[AI Fallback] ai-service unreachable for runRef={} — explanation skipped",
                request != null ? request.getRunReference() : "UNKNOWN");
        return ApiResponse.success(null, "AI service unavailable — skipped");
    }
}