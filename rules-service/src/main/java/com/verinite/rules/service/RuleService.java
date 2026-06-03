package com.verinite.rules.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verinite.rules.dto.*;
import com.verinite.rules.entity.*;
import com.verinite.rules.event.AuditEvent;
import com.verinite.rules.event.RuleEventPublisher;
import com.verinite.rules.repository.*;
import com.verinite.rules.security.UserContext;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RuleService {

    private final RuleRepository             ruleRepository;
    private final RuleAllowedValueRepository allowedValueRepository;
    private final RuleEventPublisher         eventPublisher;
    private final ObjectMapper               objectMapper;

    // ═══════════════════════════════════════════════════════════════════════
    // CREATE
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public RuleDto create(CreateRuleRequest req) {
        validateRequired(req);

        if (ruleRepository.existsByProfileIdAndMtiAndDeNumberAndIsDeletedFalse(
                req.getProfileId(), req.getMti(), req.getDeNumber())) {
            throw new IllegalStateException(
                    "Rule already exists for profileId=" + req.getProfileId()
                            + " mti=" + req.getMti()
                            + " deNumber=" + req.getDeNumber()
            );
        }

        ValidationRule rule = buildEntityFromRequest(req);
        ValidationRule saved = ruleRepository.save(rule);

        saveAllowedValues(saved, req.getAllowedValues());

        ValidationRule refreshed = ruleRepository.findByIdAndIsDeletedFalse(saved.getId())
                .orElse(saved);

        publishAudit("CREATE", "RULE", saved.getId(),
                buildEntityName(saved), null, toJson(refreshed));

        return toDto(refreshed);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // READ — effective rules (public API)
    // ═══════════════════════════════════════════════════════════════════════

    public List<RuleDto> getEffectiveRules(Long profileId, String mti) {
        return ruleRepository.findEffectiveRules(profileId, mti, LocalDate.now())
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // READ BY ID
    // ═══════════════════════════════════════════════════════════════════════

    public RuleDto getRuleById(Long id) {
        return toDto(findOrThrow(id));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // READ — raw entity list for validation-engine (internal)
    // ═══════════════════════════════════════════════════════════════════════

    public List<ValidationRule> getEffectiveRulesForEngine(Long profileId, String mti) {
        return ruleRepository.findEffectiveRules(profileId, mti, LocalDate.now());
    }

    /** Raw entity for soft-delete — caller needs profileId + mti before deleting. */
    public ValidationRule getById(Long id) {
        return findOrThrow(id);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UPDATE
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public RuleDto update(Long id, UpdateRuleRequest req) {
        ValidationRule rule   = findOrThrow(id);
        String         before = toJson(rule);

        if (req.getFieldName()     != null) rule.setFieldName(req.getFieldName());
        if (req.getIsMandatory()   != null) rule.setIsMandatory(req.getIsMandatory());
        if (req.getMinLength()     != null) rule.setMinLength(req.getMinLength());
        if (req.getMaxLength()     != null) rule.setMaxLength(req.getMaxLength());
        if (req.getExactLength()   != null) rule.setExactLength(req.getExactLength());
        if (req.getDataType()      != null) rule.setDataType(req.getDataType());
        if (req.getPatternRegex()  != null) rule.setPatternRegex(req.getPatternRegex());
        if (req.getSeverity()      != null) rule.setSeverity(req.getSeverity());
        if (req.getPriority()      != null) rule.setPriority(req.getPriority());
        if (req.getActive()        != null) rule.setActive(req.getActive());
        if (req.getEffectiveFrom() != null) rule.setEffectiveFrom(req.getEffectiveFrom());
        if (req.getEffectiveTo()   != null) rule.setEffectiveTo(req.getEffectiveTo());
        if (req.getDescription()   != null) rule.setDescription(req.getDescription());

        rule.setUpdatedBy(UserContext.getUserId());
        rule.setUpdatedByName(UserContext.getUsername());

        ValidationRule saved = ruleRepository.save(rule);

        publishAudit("UPDATE", "RULE", saved.getId(),
                buildEntityName(saved), before, toJson(saved));

        return toDto(saved);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TOGGLE STATUS (PATCH /{id}/status)
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public RuleDto toggleStatus(Long id) {
        ValidationRule rule   = findOrThrow(id);
        String         before = toJson(rule);

        rule.setActive(!Boolean.TRUE.equals(rule.getActive()));
        rule.setUpdatedBy(UserContext.getUserId());
        rule.setUpdatedByName(UserContext.getUsername());

        ValidationRule saved = ruleRepository.save(rule);

        publishAudit("UPDATE", "RULE", saved.getId(),
                buildEntityName(saved) + " [status toggled → " + saved.getActive() + "]",
                before, toJson(saved));

        return toDto(saved);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SOFT DELETE
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public void softDelete(Long id) {
        ValidationRule rule   = findOrThrow(id);
        String         before = toJson(rule);

        rule.setIsDeleted(true);
        rule.setActive(false);
        rule.setDeletedAt(LocalDateTime.now());
        rule.setUpdatedBy(UserContext.getUserId());
        rule.setUpdatedByName(UserContext.getUsername());

        ruleRepository.save(rule);

        publishAudit("DELETE", "RULE", rule.getId(),
                buildEntityName(rule), before, null);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // BULK IMPORT — single transaction, MERGE or REPLACE strategy
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public BulkImportResult bulkImport(BulkImportRulesRequest req) {
        int imported = 0, updated = 0;

        // Fetch all existing non-deleted rules for this profile+mti
        List<ValidationRule> existing = ruleRepository.findAllNonDeleted(
                req.getProfileId(), req.getMti());

        Map<String, ValidationRule> existingByDeNumber = existing.stream()
                .collect(Collectors.toMap(
                        ValidationRule::getDeNumber,
                        r -> r,
                        (a, b) -> a   // keep first on duplicate deNumbers
                ));

        if ("REPLACE".equalsIgnoreCase(req.getStrategy())) {
            // Soft-delete rules NOT in the incoming list
            Set<String> incomingDeNumbers = req.getRules().stream()
                    .map(CreateRuleRequest::getDeNumber)
                    .collect(Collectors.toSet());

            for (ValidationRule r : existing) {
                if (!incomingDeNumbers.contains(r.getDeNumber())) {
                    r.setIsDeleted(true);
                    r.setActive(false);
                    r.setDeletedAt(LocalDateTime.now());
                    ruleRepository.save(r);
                }
            }
        }

        List<ValidationRule> toSave = new ArrayList<>();

        for (int i = 0; i < req.getRules().size(); i++) {
            CreateRuleRequest ruleReq = req.getRules().get(i);

            // Propagate parent fields
            ruleReq.setProfileId(req.getProfileId());
            ruleReq.setProfileName(req.getProfileName());
            ruleReq.setMti(req.getMti());
            if (ruleReq.getPriority() == null) ruleReq.setPriority(i + 1);

            if (existingByDeNumber.containsKey(ruleReq.getDeNumber())) {
                // MERGE — update existing in-memory
                ValidationRule existing_rule = existingByDeNumber.get(ruleReq.getDeNumber());
                applyBulkUpdate(existing_rule, ruleReq);
                toSave.add(existing_rule);
                updated++;
            } else {
                // Insert new
                toSave.add(buildEntityFromRequest(ruleReq));
                imported++;
            }
        }

        ruleRepository.saveAll(toSave);

        // One cache invalidation for the whole batch
        eventPublisher.publishRuleUpdated(req.getProfileId(), req.getMti());

        // One audit event for the whole batch
        publishAudit("RULE_IMPORT", "RULE", null,
                "Bulk import: " + imported + " inserted, " + updated + " updated — "
                        + "profileId=" + req.getProfileId() + " mti=" + req.getMti(),
                null, null);

        return BulkImportResult.builder()
                .imported(imported).updated(updated).skipped(0).errors(List.of())
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // EXPORT — all non-deleted (no date filter)
    // ═══════════════════════════════════════════════════════════════════════

    public List<RuleDto> exportRules(Long profileId, String mti) {
        List<ValidationRule> rules = ruleRepository.findAllNonDeleted(profileId, mti);

        publishAudit("RULE_EXPORT", "RULE", null,
                "Export: " + rules.size() + " rules for profileId=" + profileId + " mti=" + mti,
                null, null);

        return rules.stream().map(this::toDto).collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // REORDER — batch priority update
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public void reorder(ReorderRulesRequest req) {
        for (ReorderRulesRequest.RulePriority rp : req.getPriorities()) {
            ValidationRule rule = findOrThrow(rp.getRuleId());
            rule.setPriority(rp.getPriority());
            rule.setUpdatedBy(UserContext.getUserId());
            rule.setUpdatedByName(UserContext.getUsername());
            ruleRepository.save(rule);
        }
        log.info("Reordered {} rules", req.getPriorities().size());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ALLOWED VALUES
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public void addAllowedValue(Long ruleId, String value) {
        ValidationRule rule = findOrThrow(ruleId);

        RuleAllowedValue av = RuleAllowedValue.builder()
                .rule(rule)
                .allowedValue(value)
                .createdBy(UserContext.getUserId())
                .createdByName(UserContext.getUsername())
                .build();
        allowedValueRepository.save(av);

        publishAudit("UPDATE", "RULE", ruleId,
                buildEntityName(rule) + " [allowed value added: " + value + "]",
                null, null);
    }

    @Transactional
    public void removeAllowedValue(Long ruleId, String value) {
        ValidationRule rule = findOrThrow(ruleId);
        allowedValueRepository.deleteByRuleIdAndAllowedValue(ruleId, value);

        publishAudit("UPDATE", "RULE", ruleId,
                buildEntityName(rule) + " [allowed value removed: " + value + "]",
                null, null);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    private ValidationRule findOrThrow(Long id) {
        return ruleRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new EntityNotFoundException("Rule not found: " + id));
    }

    private void validateRequired(CreateRuleRequest req) {
        if (req.getProfileId()   == null) throw new IllegalArgumentException("profileId is required");
        if (req.getProfileName() == null || req.getProfileName().isBlank())
            throw new IllegalArgumentException("profileName is required");
        if (req.getMti() == null || req.getMti().isBlank())
            throw new IllegalArgumentException("mti is required");
    }

    private ValidationRule buildEntityFromRequest(CreateRuleRequest req) {
        return ValidationRule.builder()
                .profileId(req.getProfileId())
                .profileName(req.getProfileName())
                .mti(req.getMti())
                .deNumber(req.getDeNumber())
                .fieldName(req.getFieldName())
                .isMandatory(req.getIsMandatory()  != null ? req.getIsMandatory()  : false)
                .minLength(req.getMinLength())
                .maxLength(req.getMaxLength())
                .exactLength(req.getExactLength())
                .dataType(req.getDataType())
                .patternRegex(req.getPatternRegex())
                .severity(req.getSeverity()  != null ? req.getSeverity()  : "CRITICAL")
                .priority(req.getPriority()  != null ? req.getPriority()  : 1)
                .active(req.getIsActive()    != null ? req.getIsActive()  : true)
                .effectiveFrom(req.getEffectiveFrom())
                .effectiveTo(req.getEffectiveTo())
                .description(req.getDescription())
                .isDeleted(false)
                .createdBy(UserContext.getUserId())
                .createdByName(UserContext.getUsername())
                .build();
    }

    private void applyBulkUpdate(ValidationRule rule, CreateRuleRequest req) {
        if (req.getFieldName()     != null) rule.setFieldName(req.getFieldName());
        if (req.getIsMandatory()   != null) rule.setIsMandatory(req.getIsMandatory());
        if (req.getMinLength()     != null) rule.setMinLength(req.getMinLength());
        if (req.getMaxLength()     != null) rule.setMaxLength(req.getMaxLength());
        if (req.getExactLength()   != null) rule.setExactLength(req.getExactLength());
        if (req.getDataType()      != null) rule.setDataType(req.getDataType());
        if (req.getPatternRegex()  != null) rule.setPatternRegex(req.getPatternRegex());
        if (req.getSeverity()      != null) rule.setSeverity(req.getSeverity());
        if (req.getPriority()      != null) rule.setPriority(req.getPriority());
        if (req.getEffectiveFrom() != null) rule.setEffectiveFrom(req.getEffectiveFrom());
        if (req.getEffectiveTo()   != null) rule.setEffectiveTo(req.getEffectiveTo());
        if (req.getDescription()   != null) rule.setDescription(req.getDescription());
        rule.setUpdatedBy(UserContext.getUserId());
        rule.setUpdatedByName(UserContext.getUsername());
    }

    private void saveAllowedValues(ValidationRule rule, List<String> values) {
        if (values == null || values.isEmpty()) return;
        for (String v : values) {
            allowedValueRepository.save(RuleAllowedValue.builder()
                    .rule(rule)
                    .allowedValue(v)
                    .createdBy(UserContext.getUserId())
                    .createdByName(UserContext.getUsername())
                    .build());
        }
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
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String buildEntityName(ValidationRule rule) {
        return rule.getDeNumber() + " — " + rule.getProfileName() + " — " + rule.getMti();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MAPPER
    // ═══════════════════════════════════════════════════════════════════════

    private RuleDto toDto(ValidationRule rule) {
        List<String> values = (rule.getAllowedValues() == null)
                ? List.of()
                : rule.getAllowedValues().stream()
                .map(RuleAllowedValue::getAllowedValue)
                .collect(Collectors.toList());

        return RuleDto.builder()
                .id(rule.getId())
                .profileId(rule.getProfileId())
                .profileName(rule.getProfileName())
                .mti(rule.getMti())
                .deNumber(rule.getDeNumber())
                .fieldName(rule.getFieldName())
                .isMandatory(rule.getIsMandatory())
                .minLength(rule.getMinLength())
                .maxLength(rule.getMaxLength())
                .exactLength(rule.getExactLength())
                .dataType(rule.getDataType())
                .patternRegex(rule.getPatternRegex())
                .severity(rule.getSeverity())
                .priority(rule.getPriority())
                .active(rule.getActive())
                .effectiveFrom(rule.getEffectiveFrom())
                .effectiveTo(rule.getEffectiveTo())
                .description(rule.getDescription())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .createdByName(rule.getCreatedByName())
                .updatedByName(rule.getUpdatedByName())
                .allowedValues(values)
                .build();
    }
}