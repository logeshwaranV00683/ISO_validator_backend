package com.verinite.ai.service;

import com.verinite.ai.entity.AiRunLog;
import com.verinite.ai.repository.AiRunLogRepository;
import com.verinite.common.enums.AiRunStatus;
import com.verinite.common.enums.TemplateScope;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiRunLogService {

    private final AiRunLogRepository logRepository;

    /** Log a completed call (success or failure). */
    public void log(AiRunLog runLog) {
        logRepository.save(runLog);
    }

    /** Log when AI is intentionally skipped (disabled, no errors, no template). */
    public void logSkip(String runReference,
                        String reason,
                        Long   profileId,
                        Long   templateId,
                        String correlationId) {
        logRepository.save(AiRunLog.builder()
                .runReference(runReference)
                .profileId(profileId)
                .templateId(templateId)
                .status(AiRunStatus.SKIPPED)
                .errorMessage(reason)
                .correlationId(correlationId)
                .build());
    }

    /** Log when circuit breaker is OPEN. */
    public void logCbOpen(String        runReference,
                          Long          profileId,
                          Long          templateId,
                          TemplateScope scopeUsed,
                          String        promptSent,
                          String        correlationId) {
        logRepository.save(AiRunLog.builder()
                .runReference(runReference)
                .profileId(profileId)
                .templateId(templateId)
                .templateScopeUsed(scopeUsed)
                .status(AiRunStatus.CB_OPEN)
                .promptSent(promptSent)
                .errorMessage("Circuit breaker OPEN — Ollama unreachable")
                .correlationId(correlationId)
                .build());
    }

    /** Paginated logs for admin review. */
    public Page<AiRunLog> findAll(Pageable pageable) {
        return logRepository.findAll(pageable);
    }

    /** Logs for a specific run reference. */
    public java.util.List<AiRunLog> findByRunReference(String runReference) {
        return logRepository.findByRunReferenceOrderByCreatedAtDesc(runReference);
    }
}