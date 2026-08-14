package com.verinite.ai.controller;

import com.verinite.ai.entity.AiPromptTemplate;
import com.verinite.ai.entity.AiPromptTemplateVersion;
import com.verinite.ai.service.AiTemplateService;
import com.verinite.common.dto.ApiResponse;
import com.verinite.common.enums.TemplateScope;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * FE-facing prompt API (F8).
 * Thin routing layer over AiTemplateService — maps FE path style to existing service logic.
 */
@RestController
@RequestMapping("/ai/prompts")
@RequiredArgsConstructor
public class AiPromptController {

    private final AiTemplateService templateService;

    // ── GET /ai/prompts/global ──────────────────────────────────────────────
    @GetMapping("/global")
    public ResponseEntity<ApiResponse<AiPromptTemplate>> getGlobal() {
        AiPromptTemplate t = templateService.getByScope(TemplateScope.GLOBAL)
                .stream().findFirst().orElse(null);
        return ResponseEntity.ok(ApiResponse.success(t, "OK"));
    }

    // ── PUT /ai/prompts/global ──────────────────────────────────────────────
    @PutMapping("/global")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AiPromptTemplate>> updateGlobal(
            @RequestBody AiPromptTemplate body,
            Authentication auth) {
        String username = auth != null ? auth.getName() : "system";
        AiPromptTemplate existing = templateService.getByScope(TemplateScope.GLOBAL)
                .stream().findFirst()
                .orElseThrow(() -> new RuntimeException("Global template not found"));

        if (!templateService.hasChanges(existing.getId(), body)) {
            return ResponseEntity.ok(ApiResponse.success(existing,
                    "No changes detected — template is unchanged, still v" + existing.getCurrentVersion()));
        }

        return ResponseEntity.ok(ApiResponse.success(
                templateService.update(existing.getId(), body, username), "Updated"));
    }

    // ── GET /ai/prompts/profile/{profileId} ────────────────────────────────
    @GetMapping("/profile/{profileId}")
    public ResponseEntity<ApiResponse<AiPromptTemplate>> getByProfile(
            @PathVariable Long profileId) {
        AiPromptTemplate t = templateService.getByScope(TemplateScope.PROFILE)
                .stream()
                .filter(tmpl -> profileId.equals(tmpl.getProfileId()))
                .findFirst()
                .orElse(null);
        return ResponseEntity.ok(ApiResponse.success(t, "OK"));
    }

    // ── PUT /ai/prompts/profile/{profileId} ────────────────────────────────
    @PutMapping("/profile/{profileId}")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public ResponseEntity<ApiResponse<AiPromptTemplate>> upsertProfile(
            @PathVariable Long profileId, @RequestBody AiPromptTemplate body, Authentication auth) {
        String username = auth != null ? auth.getName() : "system";
        body.setScope(TemplateScope.PROFILE);
        body.setProfileId(profileId);
        // Ensure templateName is set for new inserts (NOT NULL constraint)
        if (body.getTemplateName() == null || body.getTemplateName().isBlank()) {
            throw new IllegalArgumentException("Template cannot be empty.");
        }
        return templateService.getByScope(TemplateScope.PROFILE).stream()
                .filter(t -> profileId.equals(t.getProfileId()))
                .findFirst()
                .map(existing -> {
                    if (!templateService.hasChanges(existing.getId(), body)) {
                        return ResponseEntity.ok(ApiResponse.success(existing,
                                "No changes detected — template is unchanged, still v" + existing.getCurrentVersion()));
                    }
                    return ResponseEntity.ok(ApiResponse.success(
                            templateService.update(existing.getId(), body, username), "Updated"));
                })
                .orElseGet(() -> ResponseEntity.status(201).body(ApiResponse.success(
                        templateService.create(body, username), "Created")));
    }

    // ── DELETE /ai/prompts/profile/{profileId} ─────────────────────────────
    @DeleteMapping("/profile/{profileId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProfile(
            @PathVariable Long profileId,
            Authentication auth) {
        String username = auth != null ? auth.getName() : "system";
        templateService.getByScope(TemplateScope.PROFILE).stream()
                .filter(t -> profileId.equals(t.getProfileId()))
                .findFirst()
                .ifPresent(t -> templateService.softDelete(t.getId(), username));
        return ResponseEntity.ok(ApiResponse.success(null, "Deleted"));
    }

    // ── GET /ai/prompts/{templateId}/versions ──────────────────────────────
    @GetMapping("/{templateId}/versions")
    public ResponseEntity<ApiResponse<List<AiPromptTemplateVersion>>> getVersions(
            @PathVariable Long templateId) {
        return ResponseEntity.ok(ApiResponse.success(
                templateService.getVersionHistory(templateId), "OK"));
    }

    // ── PUT /ai/prompts/{templateId}/rollback/{version} ────────────────────
    @PutMapping("/{templateId}/rollback/{version}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AiPromptTemplate>> rollback(
            @PathVariable Long    templateId,
            @PathVariable Integer version,
            Authentication auth) {
        String username = auth != null ? auth.getName() : "system";
        // AiTemplateService.rollback() rolls back to previousVersion
        return ResponseEntity.ok(ApiResponse.success(
                templateService.rollback(templateId, version, username), "Rolled back"));
    }
}