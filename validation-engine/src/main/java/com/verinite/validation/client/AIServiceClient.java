package com.verinite.validation.client;

import com.verinite.common.dto.ApiResponse;
import com.verinite.validation.dto.AiExplainRequestDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name     = "ai-service",
        fallback = AIServiceClientFallback.class
)
public interface AIServiceClient {

    @PostMapping("/internal/ai/explain")
    ApiResponse<String> explain(@RequestBody AiExplainRequestDto request);
}