package com.verinite.validation.controller;

import com.verinite.common.dto.ApiResponse;
import com.verinite.validation.dto.BuildMessageRequest;
import com.verinite.validation.dto.BuildMessageResponse;
import com.verinite.validation.dto.ValidationRequest;
import com.verinite.validation.dto.ValidationResponse;
import com.verinite.validation.service.ValidationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/validate")
@RequiredArgsConstructor
@Slf4j
public class ValidationController {

    private final ValidationService validationService;

    /**
     * POST /api/v1/validate
     * Full 8-phase validation pipeline — always returns 200.
     * status=PARSE_ERROR if jPOS cannot unpack the message.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ValidationResponse>> validate(
            @RequestBody @Valid ValidationRequest request,
            @RequestHeader(value = "X-Auth-User-Id",   required = false) String userId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {

        log.info("Validate profileId={} correlationId={}", request.getProfileId(), correlationId);

        ValidationResponse response =
                validationService.validate(request, userId, correlationId);

        return ResponseEntity.ok(ApiResponse.success(response, "Validation complete"));
    }

    /**
     * POST /api/v1/validate/build
     * Build ISO 8583 hex message from field map using profile's packager.
     */
    @PostMapping("/build")
    public ResponseEntity<ApiResponse<BuildMessageResponse>> build(
            @RequestBody @Valid BuildMessageRequest request,
            @RequestHeader(value = "X-Auth-User-Id", required = false) String userId) {

        log.info("Build message profileId={} mti={}", request.getProfileId(), request.getMti());

        BuildMessageResponse response = validationService.buildMessage(request, userId);
        return ResponseEntity.ok(ApiResponse.success(response, "Message built successfully"));
    }
}