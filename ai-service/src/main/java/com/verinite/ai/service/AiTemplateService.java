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

    @Transactional
    public AiPromptTemplate create(AiPromptTemplate template, String createdBy) {
        template.setCreatedBy(createdBy);
        AiPromptTemplate saved = templateRepo.save(template);
        versionRepo.save(AiPromptTemplateVersion.builder()
                .templateId(saved.getId())
                .versionNumber(1)
                .promptContent(saved.getPromptTemplate())
                .changeNote("Initial version")
                .isCurrent(true)
                .createdBy(createdBy)
                .build());
        return saved;
    }

    public List<AiPromptTemplate> getAll() {
        return templateRepo.findAllByDeletedAtIsNull();
    }

    public AiPromptTemplate getById(Long id) {
        return templateRepo.findById(id)
                .filter(t -> t.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("Template not found: " + id));
    }

    @Transactional
    public AiPromptTemplate update(Long id, AiPromptTemplate updated, String updatedBy) {
        AiPromptTemplate existing = getById(id);
        int newVersion = existing.getCurrentVersion() + 1;

        versionRepo.findByTemplateIdAndIsCurrentTrue(id).ifPresent(v -> {
            v.setIsCurrent(false);
            versionRepo.save(v);
        });
        versionRepo.save(AiPromptTemplateVersion.builder()
                .templateId(id)
                .versionNumber(newVersion)
                .promptContent(updated.getPromptTemplate())
                .changeNote("Updated to version " + newVersion)
                .isCurrent(true)
                .createdBy(updatedBy)
                .build());

        existing.setTemplateName(updated.getTemplateName());
        existing.setScope(updated.getScope());
        existing.setProfileId(updated.getProfileId());
        existing.setProfileName(updated.getProfileName());
        existing.setPromptTemplate(updated.getPromptTemplate());
        existing.setVariablesUsed(updated.getVariablesUsed());
        existing.setActive(updated.getActive());
        existing.setCurrentVersion(newVersion);
        existing.setUpdatedBy(updatedBy);
        return templateRepo.save(existing);
    }

    @Transactional
    public void softDelete(Long id, String deletedBy) {
        AiPromptTemplate t = getById(id);
        t.setDeletedAt(LocalDateTime.now());
        t.setUpdatedBy(deletedBy);
        templateRepo.save(t);
    }

    // PROFILE first → GLOBAL fallback
    public AiPromptTemplate resolveTemplate(Long profileId, String mti) {
        if (profileId != null) {
            Optional<AiPromptTemplate> profileTemplate =
                    templateRepo.findByProfileIdAndActiveTrueAndDeletedAtIsNull(profileId);
            if (profileTemplate.isPresent()) {
                log.debug("Using PROFILE template for profileId={}", profileId);
                return profileTemplate.get();
            }
        }
        log.debug("No PROFILE template found, falling back to GLOBAL");
        return templateRepo.findFirstByScopeAndActiveTrueAndDeletedAtIsNull(TemplateScope.GLOBAL)
                .orElseThrow(() -> new NotFoundException("No active GLOBAL template found"));
    }

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
                .map(e -> String.format("[%s] %s — %s", e.getSeverity(), e.getDeNumber(), e.getErrorMessage()))
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