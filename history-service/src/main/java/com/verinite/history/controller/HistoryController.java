package com.verinite.history.controller;

import com.verinite.history.dto.response.ApiResponse;
import com.verinite.history.dto.response.HistoryDetailDTO;
import com.verinite.history.dto.response.HistorySummaryDTO;
import com.verinite.history.dto.response.StatsResponse;
import com.verinite.history.service.HistoryService;
import com.verinite.history.service.StatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/history")
@RequiredArgsConstructor
@Slf4j
public class HistoryController {

    private final HistoryService historyService;
    private final StatsService statsService;

    // GET /api/v1/history/runs
    // Paginated list with all filters
    @GetMapping("/runs")
    public ResponseEntity<ApiResponse<Page<HistorySummaryDTO>>> listRuns(
            @RequestParam(required = false) Long profileId,
            @RequestParam(required = false) String mti,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String responseCode,
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "20")  int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Page<HistorySummaryDTO> result = historyService.listRuns(
                profileId, mti, status, userId, responseCode,
                page, size, sortBy, sortDir);

        return ResponseEntity.ok(ApiResponse.success(result, "Runs fetched"));
    }

    // GET /api/v1/history/runs/{runReference}
    // Full detail — fields + errors + aiExplanation
    @GetMapping("/runs/{runReference}")
    public ResponseEntity<ApiResponse<HistoryDetailDTO>> getByRunReference(
            @PathVariable String runReference) {

        HistoryDetailDTO detail = historyService.getByRunReference(runReference);
        return ResponseEntity.ok(ApiResponse.success(detail, "Run found"));
    }

    // DELETE /api/v1/history/runs/{runReference}
    // Soft delete
    @DeleteMapping("/runs/{runReference}")
    public ResponseEntity<ApiResponse<Void>> softDelete(
            @PathVariable String runReference) {

        historyService.softDelete(runReference);
        return ResponseEntity.ok(ApiResponse.success(null, "Run deleted"));
    }

    // GET /api/v1/history/stats
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<StatsResponse>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(statsService.getStats(), "Stats fetched"));
    }
}