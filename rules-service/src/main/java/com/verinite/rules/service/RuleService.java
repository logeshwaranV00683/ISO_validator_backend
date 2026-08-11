package com.verinite.rules.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verinite.common.enums.Severity;
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

    private final RuleRepository ruleRepository;
    private final RuleAllowedValueRepository allowedValueRepository;
    private final RuleEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final FieldDefinitionService fieldDefinitionService;

    // ═══════════════════════════════════════════════════════════════════════
    // CREATE
    // ═══════════════════════════════════════════════════════════════════════


    @Transactional
    public RuleDto create(CreateRuleRequest req) {
        validateRequired(req);
        validateEffectiveDates(req.getEffectiveFrom(), req.getEffectiveTo());
        validateLengthAgainstFieldDefinition(
                req.getProfileId(), req.getMti(), req.getDeNumber(),
                req.getMinLength(), req.getMaxLength(), req.getExactLength());

        if (ruleRepository.existsByProfileIdAndMtiAndDeNumberAndDeletedAtIsNull(
                req.getProfileId(), req.getMti(), req.getDeNumber())) {
            throw new IllegalStateException(
                    "Rule already exists for profileId=" + req.getProfileId()
                            + " mti=" + req.getMti()
                            + " deNumber=" + req.getDeNumber()
            );
        }

        ValidationRule rule = buildEntityFromRequest(req);

// Add allowed values to the entity before save — cascade handles insert
        if (req.getAllowedValues() != null) {
            for (String v : req.getAllowedValues()) {
                RuleAllowedValue av = RuleAllowedValue.builder()
                        .rule(rule)
                        .allowedValue(v)
                        .createdBy(UserContext.getUsername())
                        .build();
                rule.getAllowedValues().add(av);
            }
        }

        ValidationRule saved = ruleRepository.save(rule);  // cascade saves allowed values too


        fieldDefinitionService.syncFromRule(saved);


        publishAudit("CREATE", "RULE", saved.getId(),
                buildEntityName(saved), null, toJson(saved),
                "Rule created successfully");

        return toDto(saved);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // READ
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

    /**
     * Raw entity for soft-delete — caller needs profileId + mti before deleting.
     */
    public ValidationRule getById(Long id) {
        return findOrThrow(id);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UPDATE
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public RuleDto update(Long id, UpdateRuleRequest req) {
        ValidationRule rule = findOrThrow(id);
        String before = toJson(rule);
        validateEffectiveDates(
                req.getEffectiveFrom() != null ? req.getEffectiveFrom() : rule.getEffectiveFrom(),
                req.getEffectiveTo()   != null ? req.getEffectiveTo()   : rule.getEffectiveTo());
        validateLengthAgainstFieldDefinition(
                rule.getProfileId(), rule.getMti(), rule.getDeNumber(),
                req.getMinLength() != null ? req.getMinLength() : rule.getMinLength(),
                req.getMaxLength() != null ? req.getMaxLength() : rule.getMaxLength(),
                req.getExactLength() != null ? req.getExactLength() : rule.getExactLength());

        if (req.getFieldName() != null) rule.setFieldName(req.getFieldName());
        if (req.getIsMandatory() != null) rule.setIsMandatory(req.getIsMandatory());
        if(! (req.getMinLength()>req.getMaxLength())){
            throw new IllegalArgumentException("Minimum length must be greater than max length");
        }
        if (req.getMinLength() != null) rule.setMinLength(req.getMinLength());
        if (req.getMaxLength() != null) rule.setMaxLength(req.getMaxLength());
        if (req.getExactLength() != null) rule.setExactLength(req.getExactLength());
        if (req.getDataType() != null) rule.setDataType(req.getDataType());
        if (req.getPatternRegex() != null) rule.setPatternRegex(req.getPatternRegex());
        if (req.getSeverity() != null) rule.setSeverity(req.getSeverity());
        if (req.getPriority() != null) rule.setPriority(req.getPriority());
        if (req.getActive() != null) rule.setActive(req.getActive());
        if (req.getEffectiveFrom() != null) rule.setEffectiveFrom(req.getEffectiveFrom());
        if (req.getEffectiveTo() != null) rule.setEffectiveTo(req.getEffectiveTo());
        if (req.getDescription() != null) rule.setDescription(req.getDescription());

        rule.setUpdatedBy(UserContext.getUsername());   // String, not Long

        ValidationRule saved = ruleRepository.save(rule);
        fieldDefinitionService.syncFromRule(saved);

        publishAudit("UPDATE", "RULE", saved.getId(),
                buildEntityName(saved), before, toJson(saved),
                "Rule updated successfully");

        return toDto(saved);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TOGGLE STATUS
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public RuleDto toggleStatus(Long id) {
        ValidationRule rule = findOrThrow(id);
        String before = toJson(rule);

        rule.setActive(!Boolean.TRUE.equals(rule.getActive()));
        rule.setUpdatedBy(UserContext.getUsername());

        ValidationRule saved = ruleRepository.save(rule);

        publishAudit("UPDATE", "RULE", saved.getId(),
                buildEntityName(saved) + " [status toggled → " + saved.getActive() + "]",
                before, toJson(saved),
                "Rule status updated successfully");

        return toDto(saved);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SOFT DELETE — set deleted_at, no is_deleted field
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public void softDelete(Long id) {
        ValidationRule rule = findOrThrow(id);
        String before = toJson(rule);

        rule.setDeletedAt(LocalDateTime.now());   // deleted_at IS NOT NULL = deleted
        rule.setActive(false);
        rule.setUpdatedBy(UserContext.getUsername());

        ruleRepository.save(rule);

        publishAudit("DELETE", "RULE", rule.getId(),
                buildEntityName(rule), before, null,
                "Rule deleted successfully");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // BULK IMPORT
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public BulkImportResult bulkImport(BulkImportRulesRequest req) {
        int imported = 0, updated = 0;

        // CHANGED: was findAllNonDeleted — now includes soft-deleted rows so a
        // re-import revives them instead of trying to insert a duplicate that
        // collides with the leftover deleted row on (profileId, mti, deNumber).
        List<ValidationRule> existing = ruleRepository.findByProfileIdAndMtiIncludingDeleted(
                req.getProfileId(), req.getMti());

        Map<String, ValidationRule> existingByDeNumber = existing.stream()
                .collect(Collectors.toMap(
                        ValidationRule::getDeNumber,
                        r -> r,
                        (a, b) -> a
                ));

        if ("REPLACE".equalsIgnoreCase(req.getStrategy())) {
            Set<String> incomingDeNumbers = req.getRules().stream()
                    .map(CreateRuleRequest::getDeNumber)
                    .collect(Collectors.toSet());

            for (ValidationRule r : existing) {
                // NEW: only touch currently-alive rows here — no point re-deleting
                // something that's already soft-deleted.
                if (r.getDeletedAt() == null && !incomingDeNumbers.contains(r.getDeNumber())) {
                    r.setDeletedAt(LocalDateTime.now());
                    r.setActive(false);
                    ruleRepository.save(r);
                }
            }
        }

        List<ValidationRule> toSave = new ArrayList<>();

        for (int i = 0; i < req.getRules().size(); i++) {
            CreateRuleRequest ruleReq = req.getRules().get(i);

            ruleReq.setProfileId(req.getProfileId());
            ruleReq.setProfileName(req.getProfileName());
            ruleReq.setMti(req.getMti());
            if (ruleReq.getPriority() == null) ruleReq.setPriority(i + 1);

            if (existingByDeNumber.containsKey(ruleReq.getDeNumber())) {
                ValidationRule existingRule = existingByDeNumber.get(ruleReq.getDeNumber());
                applyBulkUpdate(existingRule, ruleReq);
                existingRule.setDeletedAt(null); // NEW — revive if it was previously soft-deleted
                existingRule.setActive(true);    // NEW — un-deactivate on revival
                toSave.add(existingRule);
                updated++;
            } else {
                toSave.add(buildEntityFromRequest(ruleReq));
                imported++;
            }
        }

        ruleRepository.saveAll(toSave);

        eventPublisher.publishRuleUpdated(req.getProfileId(), req.getMti());

        publishAudit("RULE_IMPORT", "RULE", null,
                "Bulk import: " + imported + " inserted, " + updated + " updated — "
                        + "profileId=" + req.getProfileId() + " mti=" + req.getMti(),
                null, null,
                "Rule bulk import completed");

        return BulkImportResult.builder()
                .imported(imported).updated(updated).skipped(0).errors(List.of())
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // EXPORT
    // ═══════════════════════════════════════════════════════════════════════

    public List<RuleDto> exportRules(Long profileId, String mti) {
        List<ValidationRule> rules = ruleRepository.findAllNonDeleted(profileId, mti);

        publishAudit("RULE_EXPORT", "RULE", null,
                "Export: " + rules.size() + " rules for profileId=" + profileId + " mti=" + mti,
                null, null,
                "Rule export completed");

        return rules.stream().map(this::toDto).collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // REORDER
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public void reorder(ReorderRulesRequest req) {
        for (ReorderRulesRequest.RulePriority rp : req.getPriorities()) {
            ValidationRule rule = findOrThrow(rp.getRuleId());
            rule.setPriority(rp.getPriority());
            rule.setUpdatedBy(UserContext.getUsername());
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
                .createdBy(UserContext.getUsername())   // String
                .build();
        allowedValueRepository.save(av);

        publishAudit("UPDATE", "RULE", ruleId,
                buildEntityName(rule) + " [allowed value added: " + value + "]",
                null, null,
                "Allowed value added successfully");
    }

    @Transactional
    public void removeAllowedValue(Long ruleId, String value) {
        ValidationRule rule = findOrThrow(ruleId);
        allowedValueRepository.deleteByRuleIdAndAllowedValue(ruleId, value);

        publishAudit("UPDATE", "RULE", ruleId,
                buildEntityName(rule) + " [allowed value removed: " + value + "]",
                null, null,
                "Allowed value removed successfully");
    }

    public List<RuleDto> getAllRules(Long profileId, String mti) {
        return ruleRepository.findAllNonDeleted(profileId, mti)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    private ValidationRule findOrThrow(Long id) {
        return ruleRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new EntityNotFoundException("Rule not found: " + id));
    }

    private void validateRequired(CreateRuleRequest req) {
        if (req.getProfileId() == null)
            throw new IllegalArgumentException("profileId is required");
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
                .isMandatory(req.getIsMandatory() != null ? req.getIsMandatory() : false)
                .minLength(req.getMinLength())
                .maxLength(req.getMaxLength())
                .exactLength(req.getExactLength())
                .dataType(req.getDataType())
                .patternRegex(req.getPatternRegex())
                .severity(req.getSeverity() != null ? req.getSeverity() : Severity.CRITICAL)
                .priority(req.getPriority() != null ? req.getPriority() : 1)
                .active(req.getIsActive() != null ? req.getIsActive() : true)
                .effectiveFrom(req.getEffectiveFrom())
                .effectiveTo(req.getEffectiveTo())
                .description(req.getDescription())
                .createdBy(UserContext.getUsername())   // String username
                .build();
    }

    private void applyBulkUpdate(ValidationRule rule, CreateRuleRequest req) {
        if (req.getFieldName() != null) rule.setFieldName(req.getFieldName());
        if (req.getIsMandatory() != null) rule.setIsMandatory(req.getIsMandatory());
        if (req.getMinLength() != null) rule.setMinLength(req.getMinLength());
        if (req.getMaxLength() != null) rule.setMaxLength(req.getMaxLength());
        if (req.getExactLength() != null) rule.setExactLength(req.getExactLength());
        if (req.getDataType() != null) rule.setDataType(req.getDataType());
        if (req.getPatternRegex() != null) rule.setPatternRegex(req.getPatternRegex());
        if (req.getSeverity() != null) rule.setSeverity(req.getSeverity());
        if (req.getPriority() != null) rule.setPriority(req.getPriority());
        if (req.getEffectiveFrom() != null) rule.setEffectiveFrom(req.getEffectiveFrom());
        if (req.getEffectiveTo() != null) rule.setEffectiveTo(req.getEffectiveTo());
        if (req.getDescription() != null) rule.setDescription(req.getDescription());
        rule.setUpdatedBy(UserContext.getUsername());
    }

    private void saveAllowedValues(ValidationRule rule, List<String> values) {
        if (values == null || values.isEmpty()) return;
        for (String v : values) {
            allowedValueRepository.save(RuleAllowedValue.builder()
                    .rule(rule)
                    .allowedValue(v)
                    .createdBy(UserContext.getUsername())
                    .build());
        }
    }

    private void publishAudit(String action,
                              String entityType,
                              Long entityId,
                              String entityName,
                              String before,
                              String after,
                              String description) {
        try {
            eventPublisher.publishAudit(AuditEvent.builder()
                    .payload(AuditEvent.Payload.builder()
                            .userId(UserContext.getUserId())      // Long userId stays in audit
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
                .createdByName(rule.getCreatedBy())    // created_by stores username now
                .updatedByName(rule.getUpdatedBy())
                .allowedValues(values)
                .build();
    }

    private void validateLengthAgainstFieldDefinition(
            Long profileId, String mti, String deNumber,
            Integer minLength, Integer maxLength, Integer exactLength) {

        fieldDefinitionService.findEntity(profileId, mti, deNumber).ifPresent(fieldDef -> {
            Integer ceiling = fieldDef.getSourceMaxLength();
            if (ceiling == null) return;

            checkLengthBound("minLength",   minLength,   ceiling, deNumber);
            checkLengthBound("maxLength",   maxLength,   ceiling, deNumber);
            checkLengthBound("exactLength", exactLength, ceiling, deNumber);
        });
    }

    private void checkLengthBound(String fieldLabel, Integer value, int definedMax, String deNumber) {
        if (value == null) return; // not provided — nothing to validate
        if (value <= 0) {
            throw new IllegalArgumentException(
                    fieldLabel + " must be greater than 0 (DE" + deNumber + ")");
        }
        if (value > definedMax) {
            throw new IllegalArgumentException(
                    fieldLabel + " (" + value + ") cannot exceed the field's defined length ("
                            + definedMax + ") for DE" + deNumber);
        }
    }

    @Transactional
    public int deleteAllForFormat(Long profileId, String mti) {
        List<ValidationRule> rules = ruleRepository.findAllNonDeleted(profileId, mti);
        LocalDateTime now = LocalDateTime.now();
        String username = UserContext.getUsername();

        for (ValidationRule rule : rules) {
            rule.setDeletedAt(now);
            rule.setActive(false);
            rule.setUpdatedBy(username);
        }
        ruleRepository.saveAll(rules);

        if (!rules.isEmpty()) {
            publishAudit("DELETE", "RULE", null,
                    "Bulk delete for profileId=" + profileId + " mti=" + mti,
                    null, null,
                    rules.size() + " rules deleted (format removed)");
        }

        log.info("[Format Delete] Soft-deleted {} rules for profileId={} mti={}", rules.size(), profileId, mti);
        return rules.size();
    }

    private void validateEffectiveDates(LocalDate effectiveFrom, LocalDate effectiveTo) {
        if (effectiveFrom != null && effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException(
                    "Effective To (" + effectiveTo + ") cannot be before Effective From (" + effectiveFrom + ")");
        }
    }
}