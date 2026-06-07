package com.verinite.validation.client;

import com.verinite.common.dto.ApiResponse;
import com.verinite.validation.dto.AiExplainRequestDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for ai-service /internal/ai/explain.
 * NOT exposed through the API Gateway — service-to-service only.
 *
 * fallback = AIServiceClientFallback.class ensures validation succeeds
 * even when ai-service is down or circuit-breaker is open.
 */
@FeignClient(
        name     = "ai-service",
        fallback = AIServiceClientFallback.class
)
public interface AIServiceClient {

    @PostMapping("/internal/ai/explain")
    ApiResponse<String> explain(@RequestBody AiExplainRequestDto request);
}