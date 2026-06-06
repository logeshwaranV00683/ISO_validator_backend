package com.verinite.ai.controller;

import com.verinite.ai.dto.AiExplainRequest;
import com.verinite.ai.service.OllamaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/ai")
@RequiredArgsConstructor
@Slf4j
public class InternalAiController {

    private final OllamaService ollamaService;

    @PostMapping("/explain")
    public ResponseEntity<String> explain(@RequestBody AiExplainRequest request) {
        try {
            return ResponseEntity.ok(ollamaService.getExplanation(request));
        } catch (Exception e) {
            log.warn("AI explain failed for runRef={}: {}", request.getRunReference(), e.getMessage());
            return ResponseEntity.ok(null);   // never block validation
        }
    }
}