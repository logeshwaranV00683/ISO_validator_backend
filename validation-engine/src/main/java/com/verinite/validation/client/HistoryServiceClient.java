package com.verinite.validation.client;

import com.verinite.common.dto.ApiResponse;
import com.verinite.common.dto.HistoryDetailDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "history-service")
public interface HistoryServiceClient {

    // FIX: was "/runs/{runReference}" — that path was removed from
    // HistoryController in the F3 refactor (now "/history/{runReference}").
    // Hitting "/runs/..." matched no controller, so history-service threw
    // NoHandlerFoundException, which its catch-all @ExceptionHandler(Exception.class)
    // turned into a 500 "INTERNAL_ERROR" — the exact error both
    // GET /validate/{runReference} and POST /validate/{runReference}/rerun
    // were returning.
    @GetMapping("/history/{runReference}")
    ApiResponse<HistoryDetailDTO> getRunDetail(@PathVariable String runReference);
}