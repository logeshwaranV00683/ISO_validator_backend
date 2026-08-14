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

    public AiPromptTemplate create(AiPromptTemplate template, String createdBy) {

        validatePromptContent(template.getPromptTemplate());

        Optional<AiPromptTemplate> deleted = templateRepo
                .findByScopeAndProfileId(template.getScope(), template.getProfileId())
                .filter((t) -> t.getDeletedAt() != null);

        if (deleted.isPresent()) {
            AiPromptTemplate revived = deleted.get();

            // IMPORTANT: soft-delete never removes the old version-history rows for this
            // template (by design, so the audit trail survives a Clear). That means
            // version_number 1 (and possibly more) already exists in ai_prompt_template_versions
            // for this templateId — resetting to versionNumber(1) here would violate the
            // uk_aptv_template_version unique constraint (template_id, version_number) and
            // blow up with a 500 on every "Save after Clear". Continue the version lineage
            // instead of resetting it.
            int nextVersion = versionRepo.findByTemplateIdOrderByVersionNumberDesc(revived.getId())
                    .stream()
                    .findFirst()
                    .map(AiPromptTemplateVersion::getVersionNumber)
                    .orElse(0) + 1;

            // Only one row should ever be flagged current for a given template.
            versionRepo.findByTemplateIdAndIsCurrentTrue(revived.getId()).ifPresent(v -> {
                v.setIsCurrent(false);
                versionRepo.save(v);
            });

            revived.setDeletedAt(null);
            revived.setPromptTemplate(template.getPromptTemplate());
            revived.setTemplateName(template.getTemplateName());
            revived.setCurrentVersion(nextVersion);
            revived.setActive(true);
            revived.setUpdatedBy(createdBy);
            AiPromptTemplate saved = templateRepo.save(revived);

            versionRepo.save(AiPromptTemplateVersion.builder()
                    .templateId(saved.getId())
                    .versionNumber(nextVersion)
                    .promptContent(saved.getPromptTemplate())
                    .changeNote("Recreated after clear")
                    .isCurrent(true)
                    .createdBy(createdBy)
                    .build());

            log.info("[Template] Revived soft-deleted template id={} scope={} profileId={} newVersion={}",
                    saved.getId(), saved.getScope(), saved.getProfileId(), nextVersion);
            return saved;
        }


        template.setCreatedBy(createdBy);
        template.setCurrentVersion(1);
        AiPromptTemplate saved = templateRepo.save(template);

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


    public Optional<AiPromptTemplate> findByScopeAndProfileIdIncludingDeleted(TemplateScope scope, Long profileId) {
        return templateRepo.findByScopeAndProfileId(scope, profileId);
    }

    public AiPromptTemplate getById(Long id) {
        return templateRepo.findById(id)
                .filter(t -> t.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("Template not found: " + id));
    }


    public Optional<AiPromptTemplate> getByScopeAndProfileIdIncludingDeleted(TemplateScope scope, Long profileId) {
        return templateRepo.findByScopeAndProfileId(scope, profileId);
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

        // Only validate when content is actually being changed — a null promptTemplate
        // means "not supplied", so the existing (already-valid) content is kept as-is.
        if (updated.getPromptTemplate() != null) {
            validatePromptContent(updated.getPromptTemplate());
        }

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
    public AiPromptTemplate rollback(Long id, Integer targetVersion, String updatedBy) {
        AiPromptTemplate existing = getById(id);
        int currentVersion = existing.getCurrentVersion();

        if (targetVersion == null) targetVersion = currentVersion - 1;
        if (targetVersion < 1 || targetVersion >= currentVersion) {
            throw new IllegalArgumentException("Invalid rollback target version " + targetVersion + " (current is v" + currentVersion + ")");
        }

        Integer finalTargetVersion = targetVersion;
        AiPromptTemplateVersion targetVersionRow = versionRepo
                .findByTemplateIdAndVersionNumber(id, targetVersion)
                .orElseThrow(() -> new NotFoundException("Version " + finalTargetVersion + " not found for template id=" + id));

        int newVersion = currentVersion + 1;

        versionRepo.findByTemplateIdAndIsCurrentTrue(id).ifPresent(v -> { v.setIsCurrent(false); versionRepo.save(v); });

        versionRepo.save(AiPromptTemplateVersion.builder()
                .templateId(id).versionNumber(newVersion)
                .promptContent(targetVersionRow.getPromptContent())
                .changeNote("Rolled back to v" + targetVersion + " (now v" + newVersion + ")") // now correct
                .isCurrent(true).createdBy(updatedBy).build());

        existing.setPromptTemplate(targetVersionRow.getPromptContent());
        existing.setCurrentVersion(newVersion);
        existing.setUpdatedBy(updatedBy);

        return templateRepo.save(existing);
    }

    /**
     * Determines whether an incoming update actually differs from what is currently
     * persisted for the template, using the same partial-update semantics as update():
     * a null field on the incoming request means "not supplied" and is never treated
     * as a change (since update() would leave that field untouched anyway).
     *
     * Used to prevent no-op saves from bumping the version / inserting a redundant
     * version-history row (e.g. clicking "Save" in AI Settings without editing anything).
     */
    public boolean hasChanges(Long id, AiPromptTemplate updated) {
        AiPromptTemplate existing = getById(id);
        return isChanged(existing.getTemplateName(),   updated.getTemplateName())
                || isChanged(existing.getScope(),          updated.getScope())
                || isChanged(existing.getProfileId(),      updated.getProfileId())
                || isChanged(existing.getProfileName(),    updated.getProfileName())
                || isChanged(existing.getPromptTemplate(), updated.getPromptTemplate())
                || isChanged(existing.getVariablesUsed(),  updated.getVariablesUsed())
                || isChanged(existing.getActive(),         updated.getActive());
    }

    private boolean isChanged(Object existingValue, Object incomingValue) {
        if (incomingValue == null) return false; // not supplied -> not a change
        return !incomingValue.equals(existingValue);
    }

    /**
     * Rejects blank/whitespace-only prompt content. An empty override is NOT the same
     * as "no override" — clearing a profile override has its own explicit action
     * (DELETE /ai/prompts/profile/{id}), so a blank Save must be rejected rather than
     * silently persisted or treated as a fallback-to-global signal.
     */
    private void validatePromptContent(String promptTemplate) {
        if (promptTemplate == null || promptTemplate.isBlank()) {
            throw new IllegalArgumentException("Template cannot be empty.");
        }
    }

    public List<AiPromptTemplateVersion> getVersionHistory(Long id) {
        // Deliberately NOT using getById() here — that filters out soft-deleted templates,
        // which would make version history unreachable for a "cleared" profile override
        // even though the version rows themselves are intentionally preserved (see create()'s
        // revival logic). Version history should stay viewable regardless of delete status;
        // only truly non-existent ids should 404.
        templateRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Template not found: " + id));
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