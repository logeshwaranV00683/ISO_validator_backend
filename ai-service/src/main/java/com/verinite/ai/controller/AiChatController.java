package com.verinite.ai.controller;

import com.verinite.ai.dto.AiChatRequest;
import com.verinite.ai.service.AiChatService;
import com.verinite.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public (gateway-routed, /ai/**) endpoint backing the follow-up chatbot
 * shown under the AI Explanation card on the Message Validator page.
 *
 * Distinct from InternalAiController's /internal/ai/explain, which is
 * the one-shot, network-isolated call made automatically during
 * validation. This one is user-driven, reachable from the frontend, and
 * answers ad-hoc questions grounded in that same validation's context.
 */
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Slf4j
public class AiChatController {

    private final AiChatService aiChatService;

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<String>> chat(@Valid @RequestBody AiChatRequest request) {
        String answer = aiChatService.ask(request);
        if (answer == null) {
            return ResponseEntity.ok(ApiResponse.error(
                    "AI assistant is temporarily unavailable. Please try again.", "AI_UNAVAILABLE"));
        }
        return ResponseEntity.ok(ApiResponse.success(answer, "OK"));
    }
}