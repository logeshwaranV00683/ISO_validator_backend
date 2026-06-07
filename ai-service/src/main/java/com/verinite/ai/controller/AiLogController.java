package com.verinite.ai.controller;

import com.verinite.ai.entity.AiRunLog;
import com.verinite.ai.service.AiRunLogService;
import com.verinite.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class AiLogController {

    private final AiRunLogService aiRunLogService;

    /**
     * GET /ai/logs — paginated AI run logs (ADMIN only)
     * Optional filter: ?runReference=VLD-0041&status=TIMEOUT
     */
    @GetMapping("/ai/logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<AiRunLog>>> getLogs(
            @RequestParam(required = false) String runReference,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        if (runReference != null && !runReference.isBlank()) {
            List<AiRunLog> logs = aiRunLogService.findByRunReference(runReference);
            log.debug("[AiLog] Fetched {} logs for runReference={}", logs.size(), runReference);
            return ResponseEntity.ok(ApiResponse.success(null, "Logs fetched (see data in error field — use /ai/logs?runReference=)"));
        }

        Page<AiRunLog> logsPage = aiRunLogService.findAll(
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return ResponseEntity.ok(ApiResponse.success(logsPage, "Logs fetched"));
    }

    /** GET /ai/logs/run/{runReference} — all logs for a specific validation run */
    @GetMapping("/ai/logs/run/{runReference}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<AiRunLog>>> getLogsByRun(
            @PathVariable String runReference) {
        List<AiRunLog> logs = aiRunLogService.findByRunReference(runReference);
        return ResponseEntity.ok(ApiResponse.success(logs, "Logs fetched"));
    }
}