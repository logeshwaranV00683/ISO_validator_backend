package com.verinite.validation.controller;

import com.verinite.common.dto.ApiResponse;
import com.verinite.validation.iso.PackagerCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.packager.GenericPackager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/internal/validation")
@RequiredArgsConstructor
@Slf4j
public class InternalValidationController {

    private final PackagerCache packagerCache;

    /**
     * POST /internal/validation/validate-packager-xml
     * Validates jPOS GenericPackager XML and evicts the stale cache entry.
     * Called by profile-service after a format is updated.
     */
    @PostMapping("/validate-packager-xml")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validatePackagerXml(
            @RequestBody Map<String, String> body) {

        String xmlContent = body.get("xmlContent");
        String formatIdStr = body.get("formatId");

        if (xmlContent == null || xmlContent.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("xmlContent is required", "MISSING_XML"));
        }

        try {
            new GenericPackager(new ByteArrayInputStream(
                    xmlContent.getBytes(StandardCharsets.UTF_8)));

            // Evict stale entry so next request rebuilds from new XML
            if (formatIdStr != null) {
                packagerCache.evict(Long.parseLong(formatIdStr));
            }

            return ResponseEntity.ok(ApiResponse.success(
                    Map.of("valid", true, "formatId", formatIdStr != null ? formatIdStr : "unknown"),
                    "Packager XML is valid"));

        } catch (Exception e) {
            log.warn("Invalid packager XML for formatId={}: {}", formatIdStr, e.getMessage());
            return ResponseEntity.ok(ApiResponse.error(
                    "Invalid packager XML: " + e.getMessage(), "INVALID_PACKAGER_XML"));
        }
    }

    /**
     * GET /internal/validation/cache/stats
     * Returns live PackagerCache size — useful for monitoring.
     */
    @GetMapping("/cache/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cacheStats() {
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("packagerCacheSize", packagerCache.size()),
                "Cache stats"));
    }
}