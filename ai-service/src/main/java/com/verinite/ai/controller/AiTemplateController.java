package com.verinite.ai.controller;

import com.verinite.ai.entity.AiPromptTemplate;
import com.verinite.ai.entity.AiPromptTemplateVersion;
import com.verinite.ai.event.AiAuditEventPublisher;
import com.verinite.ai.service.AiTemplateService;
import com.verinite.common.dto.ApiResponse;
import com.verinite.common.enums.TemplateScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * POST   /ai/templates          — create (ADMIN)
 * GET    /ai/templates          — list all (any; optional ?scope=GLOBAL|PROFILE)
 * GET    /ai/templates/{id}     — single template
 * PUT    /ai/templates/{id}     — update + bump version (ADMIN)
 * PUT    /ai/templates/{id}/rollback — restore previous version (ADMIN)
 * GET    /ai/templates/{id}/versions — version history
 * DELETE /ai/templates/{id}     — soft delete (ADMIN)
 */
@RestController
@RequestMapping("/ai/templates")
@RequiredArgsConstructor
@Slf4j
public class AiTemplateController {

    private final AiTemplateService    templateService;
    private final AiAuditEventPublisher auditPublisher;

    // ── POST /ai/templates ──────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AiPromptTemplate>> create(
            @RequestBody AiPromptTemplate template,
            Authentication auth) {

        String username = auth != null ? auth.getName() : "system";
        AiPromptTemplate created = templateService.create(template, username);
        auditPublisher.publishPromptChange(created.getId(), "CREATE", username);
        return ResponseEntity.status(201)
                .body(ApiResponse.success(created, "Template created"));
    }

    // ── GET /ai/templates[?scope=GLOBAL|PROFILE] ────────────────────────────

    @GetMapping
    public ResponseEntity<ApiResponse<List<AiPromptTemplate>>> getAll(
            @RequestParam(required = false) TemplateScope scope) {

        List<AiPromptTemplate> templates = (scope != null)
                ? templateService.getByScope(scope)
                : templateService.getAll();
        return ResponseEntity.ok(ApiResponse.success(templates, "OK"));
    }

    // ── GET /ai/templates/{id} ──────────────────────────────────────────────

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AiPromptTemplate>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponse.success(templateService.getById(id), "OK"));
    }

    // ── PUT /ai/templates/{id} ──────────────────────────────────────────────

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AiPromptTemplate>> update(
            @PathVariable Long id,
            @RequestBody AiPromptTemplate template,
            Authentication auth) {

        String username = auth != null ? auth.getName() : "system";

        if (!templateService.hasChanges(id, template)) {
            AiPromptTemplate unchanged = templateService.getById(id);
            log.info("[Template] No-op save id={} — content unchanged, staying on v{}", id, unchanged.getCurrentVersion());
            return ResponseEntity.ok(ApiResponse.success(unchanged,
                    "No changes detected — template is unchanged, still v" + unchanged.getCurrentVersion()));
        }

        AiPromptTemplate updated = templateService.update(id, template, username);
        auditPublisher.publishPromptChange(id, "UPDATE", username);
        return ResponseEntity.ok(ApiResponse.success(updated, "Template updated"));
    }

    // ── PUT /ai/templates/{id}/rollback ─────────────────────────────────────

    @PutMapping("/{id}/rollback")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AiPromptTemplate>> rollback(
            @PathVariable Long id, @RequestParam(required = false) Integer targetVersion,
            Authentication auth) {

        String username = auth != null ? auth.getName() : "system";
        AiPromptTemplate rolled = templateService.rollback(id,targetVersion, username);
        auditPublisher.publishPromptChange(id, "PROMPT_ROLLBACK", username);
        log.info("[Template] Rolled back id={} by {}", id, username);
        return ResponseEntity.ok(
                ApiResponse.success(rolled, "Template rolled back to previous version"));
    }

    // ── GET /ai/templates/{id}/versions ─────────────────────────────────────

    @GetMapping("/{id}/versions")
    public ResponseEntity<ApiResponse<List<AiPromptTemplateVersion>>> getVersions(
            @PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponse.success(templateService.getVersionHistory(id), "OK"));
    }

    // ── DELETE /ai/templates/{id} ───────────────────────────────────────────

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            Authentication auth) {

        String username = auth != null ? auth.getName() : "system";
        templateService.softDelete(id, username);
        auditPublisher.publishPromptChange(id, "DELETE", username);
        return ResponseEntity.ok(ApiResponse.success(null, "Template deleted"));
    }
}