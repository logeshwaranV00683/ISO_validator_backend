package com.verinite.history.controller;

import com.verinite.history.dto.response.ApiResponse;
import com.verinite.common.dto.HistoryDetailDTO;
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
import java.util.List;

@RestController
@RequestMapping("/history")
@RequiredArgsConstructor
@Slf4j
public class HistoryController {

    private final HistoryService historyService;
    private final StatsService statsService;

    /**
     * GET /history
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<HistorySummaryDTO>>> listRuns(@RequestHeader(value = "X-Auth-User-Id", required = false) Long authUserId, @RequestParam(required = false) Long profileId, @RequestParam(required = false) String mti, @RequestParam(required = false) String status, @RequestParam(required = false) String responseCode, @RequestParam(required = false) String environment, @RequestParam(required = false) String search, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "createdAt") String sortBy, @RequestParam(defaultValue = "desc") String sortDir) {
        // FIX (data isolation): userId is now always the authenticated caller's own id,
        // never a client-suppliable query param — see HistoryService.listRuns() javadoc.
        Page<HistorySummaryDTO> result = historyService.listRuns(profileId, mti, status, authUserId, responseCode, search, fromDate, toDate, page, size, sortBy, sortDir);

        return ResponseEntity.ok(ApiResponse.success(result, "Runs fetched"));
    }

    /**
     * GET /history/{runReference}
     */
    @GetMapping("/{runReference}")
    public ResponseEntity<ApiResponse<HistoryDetailDTO>> getByRunReference(@RequestHeader(value = "X-Auth-User-Id", required = false) Long authUserId, @PathVariable String runReference) {
        return ResponseEntity.ok(ApiResponse.success(historyService.getByRunReference(runReference, authUserId), "Run found"));
    }

    /**
     * DELETE /history/{runReference}
     */
    @DeleteMapping("/{runReference}")
    public ResponseEntity<ApiResponse<Void>> softDelete(@RequestHeader(value = "X-Auth-User-Id", required = false) Long authUserId, @PathVariable String runReference) {
        historyService.softDelete(runReference, authUserId);
        return ResponseEntity.ok(ApiResponse.success(null, "Run deleted"));
    }


    @PostMapping("/bulk-delete")
    public ResponseEntity<ApiResponse<HistoryService.BulkDeleteResult>> bulkDelete(@RequestHeader(value = "X-Auth-User-Id", required = false) Long authUserId, @RequestBody List<String> runReferences) {
        HistoryService.BulkDeleteResult result = historyService.bulkSoftDelete(runReferences, authUserId);
        return ResponseEntity.ok(ApiResponse.success(result, result.deletedCount() + " deleted" + (result.skippedCount() > 0 ? ", " + result.skippedCount() + " skipped" : "")));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<StatsResponse>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(statsService.getStats(), "Stats fetched"));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) Long profileId, @RequestParam(required = false) String mti, @RequestParam(required = false) String status, @RequestParam(defaultValue = "json") String format) {

        String data = historyService.exportRuns(profileId, mti, status, format);
        String mediaType = "csv".equalsIgnoreCase(format) ? "text/csv" : "application/json";
        String filename = "validation-runs." + format.toLowerCase();

        return ResponseEntity.ok().header("Content-Disposition", "attachment; filename=\"" + filename + "\"").header("Content-Type", mediaType).body(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}