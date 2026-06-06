package com.verinite.validation.controller;

import com.verinite.common.dto.ApiResponse;
import com.verinite.validation.dto.ValidationRequest;
import com.verinite.validation.dto.ValidationResponse;
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

    private final ValidationService validationService;

    @PostMapping
    public ResponseEntity<ApiResponse<ValidationResponse>> validate(
            @RequestBody @Valid ValidationRequest request,
            @RequestHeader(value = "X-Auth-User-Id",   required = false) String userId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {

        log.info("Validate request — profileId={} correlationId={}",
                request.getProfileId(), correlationId);

        ValidationResponse response = validationService.validate(request, userId, correlationId);

        return ResponseEntity.ok(
                ApiResponse.success(response, "Validation complete"));
    }
}