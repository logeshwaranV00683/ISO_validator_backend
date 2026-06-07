package com.verinite.profile.controller;

import com.verinite.common.dto.ApiResponse;
import com.verinite.profile.dto.CreateFormatRequest;
import com.verinite.profile.dto.FormatDto;
import com.verinite.profile.dto.UpdateFormatRequest;
import com.verinite.profile.service.FormatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/formats")
@RequiredArgsConstructor
public class FormatController {

    private final FormatService formatService;

    @PostMapping
    public ResponseEntity<ApiResponse<FormatDto>> createFormat(
            @RequestBody @Valid CreateFormatRequest req) {
        FormatDto created = formatService.create(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Format created"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<FormatDto>>> getAllFormats() {
        return ResponseEntity.ok(ApiResponse.success(formatService.getAll(), "Formats fetched"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FormatDto>> getFormat(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(formatService.getById(id), "Format found"));
    }

    /**
     * PUT /formats/{id}
     * Save current XML as a version, replace with new XML, bump currentVersion.
     * Publishes FORMAT_UPDATED to cache.invalidation exchange.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<FormatDto>> updateFormat(
            @PathVariable Long id,
            @RequestBody @Valid UpdateFormatRequest req,
            @RequestHeader(value = "X-Auth-Username", defaultValue = "system") String username) {
        FormatDto updated = formatService.update(id, req.getXmlContent(), username);
        return ResponseEntity.ok(ApiResponse.success(updated, "Format updated — version bumped"));
    }

    /**
     * PUT /formats/{id}/rollback
     * Restore previous version content.
     * Publishes FORMAT_ROLLED_BACK to cache.invalidation exchange.
     */
    @PutMapping("/{id}/rollback")
    public ResponseEntity<ApiResponse<FormatDto>> rollbackFormat(@PathVariable Long id) {
        FormatDto rolled = formatService.rollback(id);
        return ResponseEntity.ok(ApiResponse.success(rolled, "Format rolled back"));
    }

    /**
     * GET /formats/{id}/versions
     * Full version history.
     */
    @GetMapping("/{id}/versions")
    public ResponseEntity<ApiResponse<List<FormatDto>>> getVersions(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(formatService.getVersions(id), "Versions fetched"));
    }
}