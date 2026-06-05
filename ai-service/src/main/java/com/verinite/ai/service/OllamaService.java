package com.verinite.ai.service;

import com.verinite.ai.client.OllamaClient;
import com.verinite.ai.dto.AiExplainRequest;
import com.verinite.ai.dto.TemplateContext;
import com.verinite.ai.entity.AiPromptTemplate;
import com.verinite.ai.entity.AiRunLog;
import com.verinite.ai.repository.OllamaConfigRepository;
import com.verinite.common.enums.AiRunStatus;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OllamaService {

    private final OllamaClient           ollamaClient;
    private final AiTemplateService      templateService;
    private final AiRunLogService        aiRunLogService;
    private final OllamaConfigRepository ollamaConfigRepo;

    public String getExplanation(AiExplainRequest request) {
        String runRef = request.getRunReference() != null ? request.getRunReference() : "UNKNOWN";

        // 1. Check if AI is enabled
        boolean enabled = Boolean.parseBoolean(
                ollamaConfigRepo.findByConfigKey("ollama.enabled")
                        .map(c -> c.getConfigValue()).orElse("false"));
        if (!enabled) {
            aiRunLogService.logSkip(runRef, "DISABLED", request.getProfileId());
            return null;
        }

        // 2. Skip if no errors
        if (request.getErrors() == null || request.getErrors().isEmpty()) {
            aiRunLogService.logSkip(runRef, "NO_ERRORS", request.getProfileId());
            return null;
        }

        // 3. Resolve template
        AiPromptTemplate template;
        try {
            template = templateService.resolveTemplate(request.getProfileId(), request.getMti());
        } catch (Exception e) {
            aiRunLogService.logSkip(runRef, "NO_TEMPLATE: " + e.getMessage(), request.getProfileId());
            return null;
        }

        // 4. Build prompt
        String prompt = templateService.substituteVariables(
                template.getPromptTemplate(),
                TemplateContext.builder()
                        .mti(request.getMti())
                        .profileName(request.getProfileName())
                        .errors(request.getErrors())
                        .parsedFields(request.getParsedFields())
                        .build());

        // 5. Call Ollama with CB
        return callWithCB(prompt, runRef, template.getId(),
                request.getProfileId(), template.getScope().name(), request.getCorrelationId());
    }

    @CircuitBreaker(name = "ollama-cb", fallbackMethod = "ollamaFallback")
    public String callWithCB(String prompt, String runRef, Long templateId,
                             Long profileId, String scopeUsed, String correlationId) {
        long startMs = System.currentTimeMillis();
        String responseText = null;
        AiRunStatus status = AiRunStatus.SUCCESS;
        int httpStatus = 200;

        try {
            responseText = ollamaClient.callOllama(prompt);
        } catch (Exception e) {
            status = AiRunStatus.FAILED;
            httpStatus = 500;
            log.warn("Ollama FAILED for runRef={}: {}", runRef, e.getMessage());
            throw e;
        } finally {
            aiRunLogService.log(AiRunLog.builder()
                    .runReference(runRef)
                    .templateId(templateId)
                    .profileId(profileId)
                    .ollamaEndpoint(ollamaClient.getEndpoint())
                    .modelName(ollamaClient.getModelName())
                    .promptSent(prompt)
                    .responseReceived(responseText)
                    .httpStatusCode(httpStatus)
                    .status(status)
                    .durationMs(System.currentTimeMillis() - startMs)
                    .correlationId(correlationId)
                    .build());
        }
        return responseText;
    }

    public String ollamaFallback(String prompt, String runRef, Long templateId,
                                 Long profileId, String scopeUsed,
                                 String correlationId, Throwable t) {
        log.warn("Ollama CB OPEN for runRef={}", runRef);
        aiRunLogService.logCbOpen(runRef, profileId, prompt);
        return null;
    }
}