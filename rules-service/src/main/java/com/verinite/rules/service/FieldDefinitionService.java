package com.verinite.rules.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verinite.rules.dto.*;
import com.verinite.rules.entity.FieldDefinition;
import com.verinite.rules.event.AuditEvent;
import com.verinite.rules.event.RuleEventPublisher;
import com.verinite.rules.repository.FieldDefinitionRepository;
import com.verinite.rules.security.UserContext;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FieldDefinitionService {

    private final FieldDefinitionRepository fieldDefinitionRepository;
    private final RuleEventPublisher        eventPublisher;
    private final ObjectMapper              objectMapper;

    // ═══════════════════════════════════════════════════════════════════════
    // CREATE
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public FieldDefinitionDto create(CreateFieldDefinitionRequest req) {
        validateRequired(req);

        if (fieldDefinitionRepository.existsByProfileIdAndMtiAndDeNumberAndIsDeletedFalse(
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
                buildEntityName(saved), null, toJson(saved));

        return toDto(saved);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // READ — visible fields only (public API)
    // ═══════════════════════════════════════════════════════════════════════

    public List<FieldDefinitionDto> getVisibleFields(Long profileId, String mti) {
        return fieldDefinitionRepository
                .findByProfileIdAndMtiAndIsBuilderVisibleTrueAndIsDeletedFalse(profileId, mti)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // READ BY ID
    // ═══════════════════════════════════════════════════════════════════════

    public FieldDefinitionDto getById(Long id) {
        return toDto(findOrThrow(id));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // READ — all fields including hidden (internal engine use)
    // ═══════════════════════════════════════════════════════════════════════

    public List<FieldDefinition> getByProfileAndMti(Long profileId, String mti) {
        return fieldDefinitionRepository.findByProfileIdAndMtiAndIsDeletedFalse(profileId, mti);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UPDATE
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public FieldDefinitionDto update(Long id, UpdateFieldDefinitionRequest req) {
        FieldDefinition fd     = findOrThrow(id);
        String          before = toJson(fd);

        if (req.getFieldName()        != null) fd.setFieldName(req.getFieldName());
        if (req.getDataType()         != null) fd.setDataType(req.getDataType());
        if (req.getMaxLength()        != null) fd.setMaxLength(req.getMaxLength());
        if (req.getIsLlvar()          != null) fd.setIsLlvar(req.getIsLlvar());
        if (req.getIsLllvar()         != null) fd.setIsLllvar(req.getIsLllvar());
        if (req.getIsMandatory()      != null) fd.setIsMandatory(req.getIsMandatory());
        if (req.getPlaceholderValue() != null) fd.setPlaceholderValue(req.getPlaceholderValue());
        if (req.getDisplayOrder()     != null) fd.setDisplayOrder(req.getDisplayOrder());
        if (req.getIsBuilderVisible() != null) fd.setIsBuilderVisible(req.getIsBuilderVisible());
        if (req.getIsActive()         != null) fd.setIsActive(req.getIsActive());

        fd.setUpdatedBy(UserContext.getUserId());
        fd.setUpdatedByName(UserContext.getUsername());

        FieldDefinition saved = fieldDefinitionRepository.save(fd);

        publishAudit("UPDATE", "FIELD_DEFINITION", saved.getId(),
                buildEntityName(saved), before, toJson(saved));

        return toDto(saved);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SOFT DELETE
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public void softDelete(Long id) {
        FieldDefinition fd     = findOrThrow(id);
        String          before = toJson(fd);

        fd.setIsDeleted(true);
        fd.setIsActive(false);
        fd.setUpdatedBy(UserContext.getUserId());
        fd.setUpdatedByName(UserContext.getUsername());

        fieldDefinitionRepository.save(fd);

        publishAudit("DELETE", "FIELD_DEFINITION", fd.getId(),
                buildEntityName(fd), before, null);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // BULK IMPORT — single transaction, MERGE or REPLACE strategy
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public BulkImportResult bulkImport(BulkImportFieldDefinitionsRequest req) {
        int imported = 0, updated = 0;

        List<FieldDefinition> existing = fieldDefinitionRepository
                .findByProfileIdAndMtiAndIsDeletedFalse(req.getProfileId(), req.getMti());

        Map<String, FieldDefinition> existingByDeNumber = existing.stream()
                .collect(Collectors.toMap(FieldDefinition::getDeNumber, f -> f, (a, b) -> a));

        if ("REPLACE".equalsIgnoreCase(req.getStrategy())) {
            Set<String> incomingDeNumbers = req.getDefinitions().stream()
                    .map(CreateFieldDefinitionRequest::getDeNumber)
                    .collect(Collectors.toSet());

            for (FieldDefinition f : existing) {
                if (!incomingDeNumbers.contains(f.getDeNumber())) {
                    f.setIsDeleted(true);
                    f.setIsActive(false);
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
                FieldDefinition existing_fd = existingByDeNumber.get(defReq.getDeNumber());
                applyBulkUpdate(existing_fd, defReq);
                toSave.add(existing_fd);
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
                null, null);

        return BulkImportResult.builder()
                .imported(imported).updated(updated).skipped(0).errors(List.of())
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    private FieldDefinition findOrThrow(Long id) {
        return fieldDefinitionRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new EntityNotFoundException("FieldDefinition not found: " + id));
    }

    private void validateRequired(CreateFieldDefinitionRequest req) {
        if (req.getProfileId()   == null) throw new IllegalArgumentException("profileId is required");
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
                .isLlvar(req.getIsLlvar()          != null ? req.getIsLlvar()          : false)
                .isLllvar(req.getIsLllvar()         != null ? req.getIsLllvar()         : false)
                .isMandatory(req.getIsMandatory()   != null ? req.getIsMandatory()      : false)
                .placeholderValue(req.getPlaceholderValue())
                .displayOrder(req.getDisplayOrder() != null ? req.getDisplayOrder()     : 0)
                .isBuilderVisible(req.getIsBuilderVisible() != null
                        ? req.getIsBuilderVisible() : true)
                .isActive(req.getIsActive()         != null ? req.getIsActive()         : true)
                .isDeleted(false)
                .createdBy(UserContext.getUserId())
                .createdByName(UserContext.getUsername())
                .build();
    }

    private void applyBulkUpdate(FieldDefinition fd, CreateFieldDefinitionRequest req) {
        if (req.getFieldName()        != null) fd.setFieldName(req.getFieldName());
        if (req.getDataType()         != null) fd.setDataType(req.getDataType());
        if (req.getMaxLength()        != null) fd.setMaxLength(req.getMaxLength());
        if (req.getIsLlvar()          != null) fd.setIsLlvar(req.getIsLlvar());
        if (req.getIsLllvar()         != null) fd.setIsLllvar(req.getIsLllvar());
        if (req.getIsMandatory()      != null) fd.setIsMandatory(req.getIsMandatory());
        if (req.getPlaceholderValue() != null) fd.setPlaceholderValue(req.getPlaceholderValue());
        if (req.getDisplayOrder()     != null) fd.setDisplayOrder(req.getDisplayOrder());
        if (req.getIsBuilderVisible() != null) fd.setIsBuilderVisible(req.getIsBuilderVisible());
        if (req.getIsActive()         != null) fd.setIsActive(req.getIsActive());
        fd.setUpdatedBy(UserContext.getUserId());
        fd.setUpdatedByName(UserContext.getUsername());
    }

    private void publishAudit(String action, String entityType,
                              Long entityId, String entityName,
                              String before, String after) {
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
                            .correlationId(UserContext.getCorrelationId())
                            .build())
                    .build());
        } catch (Exception e) {
            log.warn("Audit publish failed for {} {} — {}", action, entityId, e.getMessage());
        }
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return "{}"; }
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
                .isActive(fd.getIsActive())
                .createdAt(fd.getCreatedAt())
                .updatedAt(fd.getUpdatedAt())
                .createdByName(fd.getCreatedByName())
                .updatedByName(fd.getUpdatedByName())
                .build();
    }
}