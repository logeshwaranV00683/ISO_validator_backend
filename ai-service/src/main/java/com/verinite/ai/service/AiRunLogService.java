package com.verinite.ai.service;

import com.verinite.ai.entity.AiRunLog;
import com.verinite.ai.repository.AiRunLogRepository;
import com.verinite.common.enums.AiRunStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiRunLogService {

    private final AiRunLogRepository logRepository;

    public void log(AiRunLog log) {
        logRepository.save(log);
    }

    public void logSkip(String runReference, String reason, Long profileId) {
        logRepository.save(AiRunLog.builder()
                .runReference(runReference)
                .profileId(profileId)
                .status(AiRunStatus.SKIPPED)
                .errorMessage(reason)
                .build());
    }

    public void logCbOpen(String runReference, Long profileId, String prompt) {
        logRepository.save(AiRunLog.builder()
                .runReference(runReference)
                .profileId(profileId)
                .status(AiRunStatus.CB_OPEN)
                .promptSent(prompt)
                .errorMessage("Circuit breaker OPEN — Ollama unreachable")
                .build());
    }
}