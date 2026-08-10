package com.verinite.rules.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verinite.rules.dto.*;
import com.verinite.rules.entity.FieldDefinition;
import com.verinite.rules.event.AuditEvent;
import com.verinite.rules.event.RuleEventPublisher;
import com.verinite.rules.repository.FieldDefinitionRepository;
import com.verinite.rules.repository.RuleRepository;
import com.verinite.rules.security.UserContext;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.verinite.rules.entity.ValidationRule;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FieldDefinitionService {

    private final FieldDefinitionRepository fieldDefinitionRepository;
    private final RuleEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final RuleRepository ruleRepository;

    public Optional<FieldDefinition> findEntity(Long profileId, String mti, String deNumber) {
        return fieldDefinitionRepository.findByProfileIdAndMtiAndDeNumberAndDeletedAtIsNull(profileId, mti, deNumber);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CREATE
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public FieldDefinitionDto create(CreateFieldDefinitionRequest req) {
        validateRequired(req);

        if (fieldDefinitionRepository.existsByProfileIdAndMtiAndDeNumberAndDeletedAtIsNull(
                req.getProfileId(), req.getMti(), req.getDeNumber())) {
            throw new IllegalStateException(
                    "FieldDefinition already exists for profileId=" + req.getProfileId()
                            + " mti=" + req.getMti()
                            + " deNumber=" + req.getDeNumber()
            );
        }


        FieldDefinition fd = buildEntityFromRequest(req);
        FieldDefinition saved = fieldDefinitionRepository.save(fd);

        publishAudit("CREATE", "FIELD_DEFINITION", saved.getId(),
                buildEntityName(saved), null, toJson(saved),
                "Field Definition created successfully");

        return toDto(saved);
    }


//    @Transactional
//    public void createIfNotExists(ValidationRule rule) {
//
//        if (fieldDefinitionRepository.existsByProfileIdAndMtiAndDeNumberAndDeletedAtIsNull(
//                rule.getProfileId(),
//                rule.getMti(),
//                rule.getDeNumber())) {
//            return;
//        }
//
//        FieldDefinition fd = FieldDefinition.builder()
//                .profileId(rule.getProfileId())
//                .profileName(rule.getProfileName())
//                .mti(rule.getMti())
//                .deNumber(rule.getDeNumber())
//                .fieldName(rule.getFieldName())
//                .dataType(rule.getDataType())
//                .maxLength(rule.getMaxLength())
//                .isMandatory(rule.getIsMandatory())
//                .isLlvar(false)
//                .isLllvar(false)
//                .placeholderValue(null)
//                .displayOrder(0)
//                .isBuilderVisible(true)
//                .active(true)
//                .description(rule.getDescription())
//                .createdBy(UserContext.getUsername())
//                .build();
//
//        FieldDefinition savedFd = fieldDefinitionRepository.save(fd);
//
//        publishAudit(
//                "CREATE",
//                "FIELD_DEFINITION",
//                savedFd.getId(),
//                buildEntityName(savedFd),
//                null,
//                toJson(savedFd),
//                "Auto-created from Rule " + rule.getId()
//        );
//    }


    @Transactional
    public void syncFromRule(ValidationRule rule) {
        Optional<FieldDefinition> existing = fieldDefinitionRepository
                .findByProfileIdAndMtiAndDeNumberAndDeletedAtIsNull(
                        rule.getProfileId(), rule.getMti(), rule.getDeNumber());

        if (existing.isPresent()) {
            FieldDefinition fd = existing.get();
            String before = toJson(fd);

            fd.setFieldName(rule.getFieldName());
            fd.setDataType(rule.getDataType());
            fd.setIsMandatory(rule.getIsMandatory());
            if (rule.getMaxLength() != null) {
                fd.setMaxLength(rule.getMaxLength()); // NOT sourceMaxLength — ceiling stays untouched
            }
            fd.setUpdatedBy(UserContext.getUsername());

            FieldDefinition saved = fieldDefinitionRepository.save(fd);
            publishAudit("UPDATE", "FIELD_DEFINITION", saved.getId(),
                    buildEntityName(saved), before, toJson(saved),
                    "Synced from Rule " + rule.getId());
        } else {
            FieldDefinition fd = FieldDefinition.builder()
                    .profileId(rule.getProfileId())
                    .profileName(rule.getProfileName())
                    .mti(rule.getMti())
                    .deNumber(rule.getDeNumber())
                    .fieldName(rule.getFieldName())
                    .dataType(rule.getDataType())
                    .maxLength(rule.getMaxLength())
                    .sourceMaxLength(rule.getMaxLength()) // no XML yet — rule's value becomes the initial ceiling
                    .isMandatory(rule.getIsMandatory())
                    .isLlvar(false)
                    .isLllvar(false)
                    .placeholderValue(null)
                    .displayOrder(0)
                    .isBuilderVisible(true)
                    .active(true)
                    .description(rule.getDescription())
                    .createdBy(UserContext.getUsername())
                    .build();

            FieldDefinition saved = fieldDefinitionRepository.save(fd);
            publishAudit("CREATE", "FIELD_DEFINITION", saved.getId(),
                    buildEntityName(saved), null, toJson(saved),
                    "Auto-created from Rule " + rule.getId());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // READ — visible fields only (public API)
    // ═══════════════════════════════════════════════════════════════════════

    public List<FieldDefinitionDto> getVisibleFields(Long profileId, String mti) {
        return fieldDefinitionRepository
                .findByProfileIdAndMtiAndIsBuilderVisibleTrueAndDeletedAtIsNull(profileId, mti)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // READ BY ID
    // ═══════════════════════════════════════════════════════════════════════

    public FieldDefinitionDto getById(Long id) {
        return toDto(findOrThrow(id));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // READ — all fields including hidden (internal engine)
    // ═══════════════════════════════════════════════════════════════════════

    public List<FieldDefinition> getByProfileAndMti(Long profileId, String mti) {
        return fieldDefinitionRepository.findByProfileIdAndMtiAndDeletedAtIsNull(profileId, mti);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UPDATE
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public FieldDefinitionDto update(Long id, UpdateFieldDefinitionRequest req) {
        FieldDefinition fd = findOrThrow(id);
        String before = toJson(fd);

        if (req.getFieldName() != null) fd.setFieldName(req.getFieldName());
        if (req.getDataType() != null) fd.setDataType(req.getDataType());
        if (req.getMaxLength() != null){
            validateMaxLengthAgainstRule(fd, req.getMaxLength());
            fd.setMaxLength(req.getMaxLength());
        }
        if (req.getIsLlvar() != null) fd.setIsLlvar(req.getIsLlvar());
        if (req.getIsLllvar() != null) fd.setIsLllvar(req.getIsLllvar());
        if (req.getIsMandatory() != null) fd.setIsMandatory(req.getIsMandatory());
        if (req.getPlaceholderValue() != null) fd.setPlaceholderValue(req.getPlaceholderValue());
        if (req.getDisplayOrder() != null) fd.setDisplayOrder(req.getDisplayOrder());
        if (req.getIsBuilderVisible() != null) fd.setIsBuilderVisible(req.getIsBuilderVisible());
        if (req.getIsActive() != null) fd.setActive(req.getIsActive());
        if (req.getDescription() != null) fd.setDescription(req.getDescription());// entity field is "active"

        fd.setUpdatedBy(UserContext.getUsername());   // String

        FieldDefinition saved = fieldDefinitionRepository.save(fd);

        publishAudit("UPDATE", "FIELD_DEFINITION", saved.getId(),
                buildEntityName(saved), before, toJson(saved),
                "Field Definition updated successfully");

        return toDto(saved);
    }

    private void validateMaxLengthAgainstRule(FieldDefinition fd, Integer newMaxLength) {
        if (newMaxLength <= 0) {
            throw new IllegalArgumentException("maxLength must be greater than 0 (DE" + fd.getDeNumber() + ")");
        }

        Integer cap = ruleRepository
                .findByProfileIdAndMtiAndDeNumberAndDeletedAtIsNull(fd.getProfileId(), fd.getMti(), fd.getDeNumber())
                .map(ValidationRule::getMaxLength)
                .orElse(fd.getSourceMaxLength());

        if (cap != null && newMaxLength > cap) {
            throw new IllegalArgumentException(
                    "maxLength (" + newMaxLength + ") cannot exceed " + cap
                            + " for DE" + fd.getDeNumber()
                            + " (bounded by " + (ruleRepository.findByProfileIdAndMtiAndDeNumberAndDeletedAtIsNull(
                            fd.getProfileId(), fd.getMti(), fd.getDeNumber()).isPresent()
                            ? "the Rule's maxLength" : "the XML source length") + ")");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SOFT DELETE — set deleted_at, no is_deleted field
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public void softDelete(Long id) {
        FieldDefinition fd = findOrThrow(id);
        String before = toJson(fd);

        fd.setDeletedAt(LocalDateTime.now());   // deleted_at IS NOT NULL = deleted
        fd.setActive(false);                    // entity field is "active"
        fd.setUpdatedBy(UserContext.getUsername());

        fieldDefinitionRepository.save(fd);

        publishAudit("DELETE", "FIELD_DEFINITION", fd.getId(),
                buildEntityName(fd), before, null,
                "Field Definition deleted successfully");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // BULK IMPORT
    // ═══════════════════════════════════════════════════════════════════════

    public BulkImportResult bulkImport(BulkImportFieldDefinitionsRequest req) {
        int imported = 0, updated = 0;

        // CHANGED: include soft-deleted rows so re-imports revive them instead of
        // colliding with a leftover deleted row on (profileId, mti, deNumber).
        List<FieldDefinition> existing = fieldDefinitionRepository
                .findByProfileIdAndMti(req.getProfileId(), req.getMti());

        Map<String, FieldDefinition> existingByDeNumber = existing.stream()
                .collect(Collectors.toMap(FieldDefinition::getDeNumber, f -> f, (a, b) -> a));

        if ("REPLACE".equalsIgnoreCase(req.getStrategy())) {
            Set<String> incomingDeNumbers = req.getDefinitions().stream()
                    .map(CreateFieldDefinitionRequest::getDeNumber)
                    .collect(Collectors.toSet());

            for (FieldDefinition f : existing) {
                if (f.getDeletedAt() == null && !incomingDeNumbers.contains(f.getDeNumber())) {
                    f.setDeletedAt(LocalDateTime.now());
                    f.setActive(false);
                    fieldDefinitionRepository.save(f);
                }
            }
        }

        List<FieldDefinition> toSave = new ArrayList<>();

        for (int i = 0; i < req.getDefinitions().size(); i++) {
            CreateFieldDefinitionRequest defReq = req.getDefinitions().get(i);

            defReq.setProfileId(req.getProfileId());
            defReq.setProfileName(req.getProfileName());
            defReq.setMti(req.getMti());
            if (defReq.getDisplayOrder() == null) defReq.setDisplayOrder(i);

            if (existingByDeNumber.containsKey(defReq.getDeNumber())) {
                FieldDefinition existingFd = existingByDeNumber.get(defReq.getDeNumber());
                applyBulkUpdate(existingFd, defReq);
                existingFd.setDeletedAt(null); // NEW — revive if it was previously soft-deleted
                existingFd.setActive(true);    // NEW — un-deactivate on revival
                toSave.add(existingFd);
                updated++;
            } else {
                toSave.add(buildEntityFromRequest(defReq));
                imported++;
            }
        }

        fieldDefinitionRepository.saveAll(toSave);

        publishAudit("RULE_IMPORT", "FIELD_DEFINITION", null,
                "Bulk import: " + imported + " inserted, " + updated + " updated — "
                        + "profileId=" + req.getProfileId() + " mti=" + req.getMti(),
                null, null,
                "Field Definition bulk import completed");

        return BulkImportResult.builder()
                .imported(imported).updated(updated).skipped(0).errors(List.of())
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    private FieldDefinition findOrThrow(Long id) {
        return fieldDefinitionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new EntityNotFoundException("FieldDefinition not found: " + id));
    }

    private void validateRequired(CreateFieldDefinitionRequest req) {
        if (req.getProfileId() == null)
            throw new IllegalArgumentException("profileId is required");
        if (req.getProfileName() == null || req.getProfileName().isBlank())
            throw new IllegalArgumentException("profileName is required");
        if (req.getMti() == null || req.getMti().isBlank())
            throw new IllegalArgumentException("mti is required");
    }

    private FieldDefinition buildEntityFromRequest(CreateFieldDefinitionRequest req) {
        return FieldDefinition.builder()
                .profileId(req.getProfileId())
                .profileName(req.getProfileName())
                .mti(req.getMti())
                .deNumber(req.getDeNumber())
                .fieldName(req.getFieldName())
                .dataType(req.getDataType())
                .maxLength(req.getMaxLength())
                .sourceMaxLength(req.getMaxLength())
                .isLlvar(req.getIsLlvar() != null ? req.getIsLlvar() : false)
                .isLllvar(req.getIsLllvar() != null ? req.getIsLllvar() : false)
                .isMandatory(req.getIsMandatory() != null ? req.getIsMandatory() : false)
                .placeholderValue(req.getPlaceholderValue())
                .displayOrder(req.getDisplayOrder() != null ? req.getDisplayOrder() : 0)
                .isBuilderVisible(req.getIsBuilderVisible() != null
                        ? req.getIsBuilderVisible() : true)
                .active(req.getIsActive() != null ? req.getIsActive() : true) // entity field "active"
                .createdBy(UserContext.getUsername())
                .description(req.getDescription())
                .build();
    }

    private void applyBulkUpdate(FieldDefinition fd, CreateFieldDefinitionRequest req) {
        if (req.getFieldName() != null) fd.setFieldName(req.getFieldName());
        if (req.getDataType() != null) fd.setDataType(req.getDataType());
        if (req.getMaxLength() != null) {
            fd.setMaxLength(req.getMaxLength());
            fd.setSourceMaxLength(req.getMaxLength());
        }
        if (req.getIsLlvar() != null) fd.setIsLlvar(req.getIsLlvar());
        if (req.getIsLllvar() != null) fd.setIsLllvar(req.getIsLllvar());
        if (req.getIsMandatory() != null) fd.setIsMandatory(req.getIsMandatory());
        if (req.getPlaceholderValue() != null) fd.setPlaceholderValue(req.getPlaceholderValue());
        if (req.getDisplayOrder() != null) fd.setDisplayOrder(req.getDisplayOrder());
        if (req.getIsBuilderVisible() != null) fd.setIsBuilderVisible(req.getIsBuilderVisible());
        if (req.getIsActive() != null) fd.setActive(req.getIsActive());
        if (req.getDescription() != null) fd.setDescription(req.getDescription());
        fd.setUpdatedBy(UserContext.getUsername());
    }

    private void publishAudit(String action, String entityType,
                              Long entityId, String entityName,
                              String before, String after, String description) {
        try {
            eventPublisher.publishAudit(AuditEvent.builder()
                    .payload(AuditEvent.Payload.builder()
                            .userId(UserContext.getUserId())
                            .username(UserContext.getUsername())
                            .userRole(UserContext.getRole())
                            .action(action)
                            .entityType(entityType)
                            .entityId(entityId)
                            .entityName(entityName)
                            .beforeValue(before)
                            .afterValue(after)
                            .description(description)
                            .correlationId(UserContext.getCorrelationId())
                            .build())
                    .build());
        } catch (Exception e) {
            log.warn("Audit publish failed for {} {} — {}", action, entityId, e.getMessage());
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String buildEntityName(FieldDefinition fd) {
        return fd.getDeNumber() + " — " + fd.getProfileName() + " — " + fd.getMti();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MAPPER
    // ═══════════════════════════════════════════════════════════════════════

    FieldDefinitionDto toDto(FieldDefinition fd) {
        return FieldDefinitionDto.builder()
                .id(fd.getId())
                .profileId(fd.getProfileId())
                .profileName(fd.getProfileName())
                .mti(fd.getMti())
                .deNumber(fd.getDeNumber())
                .fieldName(fd.getFieldName())
                .dataType(fd.getDataType())
                .maxLength(fd.getMaxLength())
                .isLlvar(fd.getIsLlvar())
                .isLllvar(fd.getIsLllvar())
                .isMandatory(fd.getIsMandatory())
                .placeholderValue(fd.getPlaceholderValue())
                .displayOrder(fd.getDisplayOrder())
                .isBuilderVisible(fd.getIsBuilderVisible())
                .isActive(fd.getActive())           // entity "active" → DTO "isActive"
                .createdAt(fd.getCreatedAt())
                .updatedAt(fd.getUpdatedAt())
                .createdByName(fd.getCreatedBy())   // created_by stores username now
                .updatedByName(fd.getUpdatedBy())
                .description(fd.getDescription())
                .build();
    }

    @Transactional
    public int deleteAllForFormat(Long profileId, String mti) {
        List<FieldDefinition> defs = fieldDefinitionRepository.findByProfileIdAndMtiAndDeletedAtIsNull(profileId, mti);
        LocalDateTime now = LocalDateTime.now();

        for (FieldDefinition def : defs) {
            def.setDeletedAt(now);
            def.setActive(false);
        }
        fieldDefinitionRepository.saveAll(defs);

        log.info("[Format Delete] Soft-deleted {} field definitions for profileId={} mti={}", defs.size(), profileId, mti);
        return defs.size();
    }
}