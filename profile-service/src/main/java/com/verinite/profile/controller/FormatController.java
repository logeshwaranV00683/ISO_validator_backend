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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/formats")
@RequiredArgsConstructor
public class FormatController {

    private final FormatService formatService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<FormatDto>> createFormat(
            @RequestBody @Valid CreateFormatRequest req,
            @RequestHeader(value = "X-Auth-Username", defaultValue = "system") String username) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(formatService.create(req, username), "Format created"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<FormatDto>>> getAllFormats() {
        return ResponseEntity.ok(ApiResponse.success(formatService.getAll(), "Formats fetched"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FormatDto>> getFormat(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(formatService.getById(id), "Format found"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<FormatDto>> updateFormat(
            @PathVariable Long id,
            @RequestBody @Valid UpdateFormatRequest req,
            @RequestHeader(value = "X-Auth-Username", defaultValue = "system") String username) {
        return ResponseEntity.ok(ApiResponse.success(
                formatService.update(id, req.getXmlContent(), username), "Format updated"));
    }

    /** DELETE /formats/{id} — soft delete */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteFormat(@PathVariable Long id) {
        formatService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Format deleted"));
    }

    /** PATCH /formats/{id}/status?active=true|false */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> setStatus(
            @PathVariable Long id, @RequestParam boolean active) {
        formatService.setActive(id, active);
        return ResponseEntity.ok(ApiResponse.success(null,
                active ? "Format activated" : "Format deactivated"));
    }

    /** POST /formats/validate-xml — dry-run jPOS validation, no DB write */
    @PostMapping("/validate-xml")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> validateXml(@RequestBody String xmlContent) {
        formatService.validateXml(xmlContent);
        return ResponseEntity.ok(ApiResponse.success("XML is valid", "Validation passed"));
    }

    /** POST /formats/{id}/reload — force cache reload signal */
    @PostMapping("/{id}/reload")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> reload(@PathVariable Long id) {
        formatService.reload(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Reload signal sent"));
    }

    @PutMapping("/{id}/rollback")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<FormatDto>> rollback(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(formatService.rollback(id), "Format rolled back"));
    }

    @GetMapping("/{id}/versions")
    public ResponseEntity<ApiResponse<List<FormatDto>>> getVersions(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(formatService.getVersions(id), "Versions fetched"));
    }

    @PutMapping("/{id}/rollback/{version}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<FormatDto>> rollback(
            @PathVariable Long    id,
            @PathVariable Integer version) {
        return ResponseEntity.ok(ApiResponse.success(
                formatService.rollbackToVersion(id, version), "Format rolled back to version " + version));
    }
}