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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/history")
@RequiredArgsConstructor
@Slf4j
public class HistoryController {

    private final HistoryService historyService;
    private final StatsService   statsService;

    /** GET /history — was GET /history/runs — /runs subpath removed (F3) */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<HistorySummaryDTO>>> listRuns(
            @RequestParam(required = false) Long   profileId,
            @RequestParam(required = false) String mti,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long   userId,
            @RequestParam(required = false) String responseCode,
            @RequestParam(required = false) String environment,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0")         int    page,
            @RequestParam(defaultValue = "20")        int    size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc")      String sortDir) {

        Page<HistorySummaryDTO> result = historyService.listRuns(
                profileId, mti, status, userId, responseCode,
                dateFrom, dateTo, page, size, sortBy, sortDir);

        return ResponseEntity.ok(ApiResponse.success(result, "Runs fetched"));
    }

    /** GET /history/{runReference} — was GET /history/runs/{runReference} */
    @GetMapping("/{runReference}")
    public ResponseEntity<ApiResponse<HistoryDetailDTO>> getByRunReference(
            @PathVariable String runReference) {
        return ResponseEntity.ok(ApiResponse.success(
                historyService.getByRunReference(runReference), "Run found"));
    }

    /** DELETE /history/{runReference} — was DELETE /history/runs/{runReference} */
    @DeleteMapping("/{runReference}")
    public ResponseEntity<ApiResponse<Void>> softDelete(
            @PathVariable String runReference) {
        historyService.softDelete(runReference);
        return ResponseEntity.ok(ApiResponse.success(null, "Run deleted"));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<StatsResponse>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(statsService.getStats(), "Stats fetched"));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) Long   profileId,
            @RequestParam(required = false) String mti,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "json") String format) {

        String data = historyService.exportRuns(profileId, mti, status, format);
        String mediaType = "csv".equalsIgnoreCase(format) ? "text/csv" : "application/json";
        String filename  = "validation-runs." + format.toLowerCase();

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .header("Content-Type", mediaType)
                .body(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}