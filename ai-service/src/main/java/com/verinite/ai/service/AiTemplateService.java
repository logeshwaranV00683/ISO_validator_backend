package com.verinite.ai.service;

import com.verinite.ai.dto.TemplateContext;
import com.verinite.ai.dto.ValidationErrorDto;
import com.verinite.ai.entity.AiPromptTemplate;
import com.verinite.ai.entity.AiPromptTemplateVersion;
import com.verinite.common.enums.TemplateScope;
import com.verinite.ai.exception.NotFoundException;
import com.verinite.ai.repository.AiPromptTemplateRepository;
import com.verinite.ai.repository.AiPromptTemplateVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiTemplateService {

    private final AiPromptTemplateRepository        templateRepo;
    private final AiPromptTemplateVersionRepository versionRepo;

    // ── CRUD ────────────────────────────────────────────────────────────────

    @Transactional
    public AiPromptTemplate create(AiPromptTemplate template, String createdBy) {
        template.setCreatedBy(createdBy);
        template.setCurrentVersion(1);
        AiPromptTemplate saved = templateRepo.save(template);

        // Insert version row 1 as current
        versionRepo.save(AiPromptTemplateVersion.builder()
                .templateId(saved.getId())
                .versionNumber(1)
                .promptContent(saved.getPromptTemplate())
                .changeNote("Initial version")
                .isCurrent(true)
                .createdBy(createdBy)
                .build());

        log.info("[Template] Created id={} scope={} profileId={}",
                saved.getId(), saved.getScope(), saved.getProfileId());
        return saved;
    }

    public List<AiPromptTemplate> getAll() {
        return templateRepo.findAllByDeletedAtIsNull();
    }

    public List<AiPromptTemplate> getByScope(TemplateScope scope) {
        return templateRepo.findByScopeAndDeletedAtIsNull(scope);
    }

    public AiPromptTemplate getById(Long id) {
        return templateRepo.findById(id)
                .filter(t -> t.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("Template not found: " + id));
    }

    /**
     * PUT /ai/templates/{id}
     * Bumps currentVersion, marks the old version row as not-current,
     * inserts a NEW version row as current, updates the template content.
     * Only updates fields that are provided (non-null) in the request.
     */
    @Transactional
    public AiPromptTemplate update(Long id, AiPromptTemplate updated, String updatedBy) {
        AiPromptTemplate existing = getById(id);
        int newVersion = existing.getCurrentVersion() + 1;

        // Mark previous current version as not-current
        versionRepo.findByTemplateIdAndIsCurrentTrue(id).ifPresent(v -> {
            v.setIsCurrent(false);
            versionRepo.save(v);
        });

        // Insert new version row — use existing content if updated content is null
        versionRepo.save(AiPromptTemplateVersion.builder()
                .templateId(id)
                .versionNumber(newVersion)
                .promptContent(updated.getPromptTemplate() != null
                        ? updated.getPromptTemplate()
                        : existing.getPromptTemplate())
                .changeNote("Updated to v" + newVersion)
                .isCurrent(true)
                .createdBy(updatedBy)
                .build());

        // Update the main template row — only update fields that are provided
        if (updated.getTemplateName() != null) existing.setTemplateName(updated.getTemplateName());
        if (updated.getScope() != null) existing.setScope(updated.getScope());
        if (updated.getProfileId() != null) existing.setProfileId(updated.getProfileId());
        if (updated.getProfileName() != null) existing.setProfileName(updated.getProfileName());
        if (updated.getPromptTemplate() != null) existing.setPromptTemplate(updated.getPromptTemplate());
        if (updated.getVariablesUsed() != null) existing.setVariablesUsed(updated.getVariablesUsed());
        existing.setActive(updated.getActive() != null ? updated.getActive() : existing.getActive());
        existing.setCurrentVersion(newVersion);
        existing.setUpdatedBy(updatedBy);

        AiPromptTemplate saved = templateRepo.save(existing);
        log.info("[Template] Updated id={} newVersion={}", id, newVersion);
        return saved;
    }

    /**
     * PUT /ai/templates/{id}/rollback
     * Restores the content of (currentVersion - 1) by creating a NEW version
     * (currentVersion + 1) with the old content. Version history is preserved.
     *
     * Example: current=3 → find version 2 content → create version 4 with v2 content.
     */
    @Transactional
    public AiPromptTemplate rollback(Long id, String updatedBy) {
        AiPromptTemplate existing = getById(id);
        int currentVersion = existing.getCurrentVersion();

        if (currentVersion <= 1) {
            throw new NotFoundException("No previous version to rollback to for template id=" + id);
        }

        int targetVersion = currentVersion - 1;

        // Get the content of the previous version
        AiPromptTemplateVersion prevVersion = versionRepo
                .findByTemplateIdAndVersionNumber(id, targetVersion)
                .orElseThrow(() -> new NotFoundException(
                        "Version " + targetVersion + " not found for template id=" + id));

        int newVersion = currentVersion + 1;

        // Mark the current version as not-current
        versionRepo.findByTemplateIdAndIsCurrentTrue(id).ifPresent(v -> {
            v.setIsCurrent(false);
            versionRepo.save(v);
        });

        // Create new version with old content (history-preserving rollback)
        versionRepo.save(AiPromptTemplateVersion.builder()
                .templateId(id)
                .versionNumber(newVersion)
                .promptContent(prevVersion.getPromptContent())
                .changeNote("Rolled back to v" + targetVersion + " (now v" + newVersion + ")")
                .isCurrent(true)
                .createdBy(updatedBy)
                .build());

        // Update main template with the restored content
        existing.setPromptTemplate(prevVersion.getPromptContent());
        existing.setCurrentVersion(newVersion);
        existing.setUpdatedBy(updatedBy);

        AiPromptTemplate saved = templateRepo.save(existing);
        log.info("[Template] Rolled back id={} from v{} to v{} (new v{})",
                id, currentVersion, targetVersion, newVersion);
        return saved;
    }

    public List<AiPromptTemplateVersion> getVersionHistory(Long id) {
        getById(id); // validates the template exists and is not deleted
        return versionRepo.findByTemplateIdOrderByVersionNumberDesc(id);
    }

    @Transactional
    public void softDelete(Long id, String deletedBy) {
        AiPromptTemplate t = getById(id);
        t.setDeletedAt(LocalDateTime.now());
        t.setUpdatedBy(deletedBy);
        templateRepo.save(t);
        log.info("[Template] Soft-deleted id={}", id);
    }

    // ── Scope resolution ────────────────────────────────────────────────────

    /**
     * Scope resolution: PROFILE-scope template → use it.
     * If no PROFILE override exists → fall back to the GLOBAL template.
     */
    public AiPromptTemplate resolveTemplate(Long profileId, String mti) {
        // Try PROFILE-scope first
        if (profileId != null) {
            Optional<AiPromptTemplate> profileTemplate =
                    templateRepo.findByProfileIdAndActiveTrueAndDeletedAtIsNull(profileId);
            if (profileTemplate.isPresent()) {
                log.debug("[Template] Using PROFILE template for profileId={}", profileId);
                return profileTemplate.get();
            }
        }
        // Fall back to GLOBAL
        log.debug("[Template] No PROFILE template for profileId={} — using GLOBAL fallback", profileId);
        return templateRepo.findFirstByScopeAndActiveTrueAndDeletedAtIsNull(TemplateScope.GLOBAL)
                .orElseThrow(() -> new NotFoundException("No active GLOBAL prompt template found"));
    }

    /**
     * BRD AI Feature: resolves the single active BRD_PARSE prompt template.
     * There is no PROFILE-scope override concept for BRD parsing — it's a
     * global, document-agnostic extraction prompt.
     */
    public AiPromptTemplate resolveBrdTemplate() {
        return templateRepo
                .findFirstByScopeAndActiveTrueAndDeletedAtIsNull(TemplateScope.BRD_PARSE)
                .orElseThrow(() -> new NotFoundException(
                        "No active BRD_PARSE prompt template found. Seed the DB first."));
    }

    // ── Variable substitution ───────────────────────────────────────────────

    /**
     * Replaces {mti}, {profile}, {errors}, {fields} placeholders in the prompt.
     */
    public String substituteVariables(String template, TemplateContext ctx) {
        return template
                .replace("{mti}",     ctx.getMti()         != null ? ctx.getMti()         : "")
                .replace("{profile}", ctx.getProfileName() != null ? ctx.getProfileName() : "")
                .replace("{errors}",  formatErrors(ctx.getErrors()))
                .replace("{fields}",  formatFields(ctx.getParsedFields()));
    }

    private String formatErrors(List<ValidationErrorDto> errors) {
        if (errors == null || errors.isEmpty()) return "No errors found.";
        return errors.stream()
                .map(e -> String.format("[%s] %s (%s) — %s",
                        e.getSeverity() != null ? e.getSeverity() : "CRITICAL",
                        e.getDeNumber()  != null ? e.getDeNumber()  : "?",
                        e.getErrorCode() != null ? e.getErrorCode() : "",
                        e.getErrorMessage()))
                .collect(Collectors.joining("\n"));
    }

    private String formatFields(Map<Integer, String> fields) {
        if (fields == null || fields.isEmpty()) return "No fields parsed.";
        return fields.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> "DE" + e.getKey() + " = " + e.getValue())
                .collect(Collectors.joining("\n"));
    }
}