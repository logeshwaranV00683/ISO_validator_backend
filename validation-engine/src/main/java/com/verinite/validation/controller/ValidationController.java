package com.verinite.validation.controller;

import com.verinite.common.dto.ApiResponse;
import com.verinite.common.dto.HistoryDetailDTO;
import com.verinite.validation.client.HistoryServiceClient;
import com.verinite.validation.dto.*;
import com.verinite.validation.service.ValidationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/validate")
@RequiredArgsConstructor
@Slf4j
public class ValidationController {

    private final ValidationService    validationService;
    private final HistoryServiceClient historyClient;

    /** POST /api/v1/validate */
    @PostMapping
    public ResponseEntity<ApiResponse<ValidationResponse>> validate(
            @RequestBody @Valid ValidationRequest request,
            @RequestHeader(value = "X-Auth-User-Id",   required = false) String userId,
            @RequestHeader(value = "X-Auth-Role", required = false) String userRole,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {

        log.info("Validate profileId={} correlationId={}", request.getProfileId(), correlationId);
        ValidationResponse response = validationService.validate(request, userId, correlationId);
        return ResponseEntity.ok(ApiResponse.success(response, "Validation complete"));
    }

    /** POST /api/v1/validate/build */
    @PostMapping("/build")
    public ResponseEntity<ApiResponse<BuildMessageResponse>> build(
            @RequestBody @Valid BuildMessageRequest request,
            @RequestHeader(value = "X-Auth-User-Id", required = false) String userId) {

        log.info("Build message profileId={} mti={}", request.getProfileId(), request.getMti());
        BuildMessageResponse response = validationService.buildMessage(request, userId);
        return ResponseEntity.ok(ApiResponse.success(response, "Message built successfully"));
    }

    /**
     * GET /api/v1/validate/{runReference}
     * Proxied to history-service — validation-engine is stateless.
     */
    @GetMapping("/{runReference}")
    public ResponseEntity<ApiResponse<Object>> getRunDetail(
            @PathVariable String runReference) {
        log.info("Fetching run detail runReference={}", runReference);
        ApiResponse<HistoryDetailDTO> detail = historyClient.getRunDetail(runReference);
        return ResponseEntity.ok(ApiResponse.success(detail.getData(), "Run found"));
    }

    /**
     * POST /api/v1/validate/{runReference}/rerun
     * Fetch original raw message from history and re-validate with current rules.
     */
    @PostMapping("/{runReference}/rerun")
    public ResponseEntity<ApiResponse<ValidationResponse>> rerun(
            @PathVariable String runReference,
            @RequestHeader(value = "X-Auth-User-Id",   required = false) String userId,
            @RequestHeader(value = "X-Auth-Role", required = false) String userRole,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {

        log.info("Rerun runReference={} by userId={}", runReference, userId);
        ValidationResponse response = validationService.rerun(runReference, userId, correlationId);
        return ResponseEntity.ok(ApiResponse.success(response, "Rerun complete"));
    }
}