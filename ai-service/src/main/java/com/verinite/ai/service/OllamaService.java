package com.verinite.ai.service;

import com.verinite.ai.client.OllamaClient;
import com.verinite.ai.dto.AiExplainRequest;
import com.verinite.ai.dto.AiExplainResponse;
import com.verinite.ai.dto.TemplateContext;
import com.verinite.ai.entity.AiPromptTemplate;
import com.verinite.ai.entity.AiRunLog;
import com.verinite.ai.entity.OllamaConfig;
import com.verinite.ai.repository.OllamaConfigRepository;
import com.verinite.common.enums.AiRunStatus;
import com.verinite.common.enums.TemplateScope;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orchestrates: enabled-check → template resolve → prompt build → Ollama call (with CB).
 * NEVER throws to caller — all failures degrade gracefully to null.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OllamaService {

    private final OllamaClient           ollamaClient;
    private final AiTemplateService      templateService;
    private final AiRunLogService        aiRunLogService;
    private final OllamaConfigRepository ollamaConfigRepo;

    public AiExplainResponse getExplanation(AiExplainRequest request) {
        String runRef = request.getRunReference() != null ? request.getRunReference() : "UNKNOWN";

        // ── 1. Global AI enabled check ──────────────────────────────────────
        boolean enabled = Boolean.parseBoolean(
                ollamaConfigRepo.findByConfigKey("ollama.enabled")
                        .map(OllamaConfig::getConfigValue).orElse("false"));
        if (!enabled) {
            aiRunLogService.logSkip(runRef, "DISABLED", request.getProfileId(), null, null);
            return null;
        }

        // ── 2. Nothing to explain if there are no errors ────────────────────
        if (request.getErrors() == null || request.getErrors().isEmpty()) {
            aiRunLogService.logSkip(runRef, "NO_ERRORS", request.getProfileId(), null, null);
            return null;
        }

        // ── 3. Resolve template (PROFILE > GLOBAL) ─────────────────────────
        AiPromptTemplate template;
        try {
            template = templateService.resolveTemplate(request.getProfileId(), request.getMti());
        } catch (Exception e) {
            String reason = "NO_TEMPLATE: " + e.getMessage();
            aiRunLogService.logSkip(runRef, reason, request.getProfileId(), null, null);
            return null;
        }

        // ── 4. Build prompt via variable substitution ───────────────────────
        String prompt = templateService.substituteVariables(
                template.getPromptTemplate(),
                TemplateContext.builder()
                        .mti(request.getMti())
                        .profileName(request.getProfileName())
                        .errors(request.getErrors())
                        .parsedFields(request.getParsedFields())
                        .build());

        // ── 5. Call Ollama wrapped in Circuit Breaker ───────────────────────
        return callWithCB(prompt, runRef,
                template.getId(),
                template.getScope(),
                request.getProfileId(),
                request.getCorrelationId());
    }

    /**
     * Actual Ollama call.
     * Circuit Breaker wraps this — on OPEN state, ollamaFallback is invoked instead.
     *
     * Logging strategy:
     *  - SUCCESS / FAILED → logged in the finally block here
     *  - CB_OPEN           → logged in ollamaFallback ONLY when
     *                        t instanceof CallNotPermittedException
     *  This prevents double-logging when the method itself throws (CB is CLOSED
     *  but Ollama is down): finally logs FAILED; fallback sees a non-CB exception
     *  and skips the second log.
     */
    @CircuitBreaker(name = "ollama-cb", fallbackMethod = "ollamaFallback")
    public AiExplainResponse callWithCB(String prompt,
                             String runRef,
                             Long   templateId,
                             TemplateScope scopeUsed,
                             Long   profileId,
                             String correlationId) {

        long startMs      = System.currentTimeMillis();
        String responseText = null;
        AiRunStatus status  = AiRunStatus.SUCCESS;
        int httpStatus      = 200;
        String errorMsg     = null;

        try {
            responseText = ollamaClient.callOllama(prompt);
        } catch (Exception e) {
            status   = AiRunStatus.FAILED;
            httpStatus = 500;
            errorMsg  = e.getMessage();
            log.warn("[AI] Ollama FAILED runRef={}: {}", runRef, e.getMessage());
            throw e;   // re-throw so Resilience4j records the failure
        } finally {
            // Always log when this method body executes (SUCCESS or FAILED)
            aiRunLogService.log(AiRunLog.builder()
                    .runReference(runRef)
                    .templateId(templateId)
                    .templateScopeUsed(scopeUsed)
                    .profileId(profileId)
                    .ollamaEndpoint(ollamaClient.getEndpoint())
                    .modelName(ollamaClient.getModelName())
                    .promptSent(prompt)
                    .responseReceived(responseText)
                    .httpStatusCode(httpStatus)
                    .status(status)
                    .durationMs(System.currentTimeMillis() - startMs)
                    .errorMessage(errorMsg)
                    .correlationId(correlationId)
                    .build());
        }
        return responseText!=null?AiExplainResponse.builder()
                .explanation(responseText)
                .modelUsed(ollamaClient.getModelName())
                .build():null;
    }

    /**
     * Resilience4j fallback — called when:
     * (a) CB is OPEN                  → t is CallNotPermittedException → log CB_OPEN
     * (b) callWithCB threw an error   → t is something else → already logged in finally; skip
     */
    public AiExplainResponse ollamaFallback(String prompt,
                                 String runRef,
                                 Long   templateId,
                                 TemplateScope scopeUsed,
                                 Long   profileId,
                                 String correlationId,
                                 Throwable t) {
        if (t instanceof CallNotPermittedException) {
            // CB is OPEN — callWithCB body never ran, so we must log here
            log.warn("[AI] Circuit breaker OPEN for runRef={}", runRef);
            aiRunLogService.logCbOpen(runRef, profileId, templateId, scopeUsed, prompt, correlationId);
        } else {
            // callWithCB already logged in its finally block; don't double-log
            log.warn("[AI] Ollama fallback (method threw) for runRef={}: {}", runRef,
                    t != null ? t.getMessage() : "unknown");
        }
        return null;   // validation response gets aiExplanation=null — caller handles gracefully
    }
}