package com.verinite.rules.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verinite.common.enums.DataType;
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

        if (Boolean.TRUE.equals(req.getIsLlvar())
                && Boolean.TRUE.equals(req.getIsLllvar())) {
            throw new IllegalArgumentException(
                    "Field cannot be both LLVAR and LLLVAR");
        }
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
            if (fd.getPlaceholderValue() == null || fd.getPlaceholderValue().isBlank()) {
                fd.setPlaceholderValue(generatePlaceholder(fd.getDataType(), fd.getMaxLength(),
                        fd.getIsLlvar(), fd.getIsLllvar(), fd.getDeNumber(), fd.getFieldName()));
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
                    .placeholderValue(generatePlaceholder(rule.getDataType(), rule.getMaxLength(), false, false, rule.getDeNumber(), rule.getFieldName()))
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
                .findByProfileIdAndMtiAndIsBuilderVisibleTrueAndDeletedAtIsNullOrderByDisplayOrderAsc(profileId, mti)
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
        return fieldDefinitionRepository.findByProfileIdAndMtiAndDeletedAtIsNullOrderByDisplayOrderAsc(profileId, mti);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UPDATE
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public FieldDefinitionDto update(Long id, UpdateFieldDefinitionRequest req) {
        FieldDefinition fd = findOrThrow(id);
        String before = toJson(fd);
        Boolean llvar =
                req.getIsLlvar() != null ? req.getIsLlvar() : fd.getIsLlvar();

        Boolean lllvar =
                req.getIsLllvar() != null ? req.getIsLllvar() : fd.getIsLllvar();

        if (Boolean.TRUE.equals(llvar)
                && Boolean.TRUE.equals(lllvar)) {
            throw new IllegalArgumentException(
                    "Field cannot be both LLVAR and LLLVAR");
        }

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
                .placeholderValue(resolvePlaceholder(req.getPlaceholderValue(), req.getDataType(),
                        req.getMaxLength(), req.getIsLlvar(), req.getIsLllvar(), req.getDeNumber(), req.getFieldName()))
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
        if (req.getPlaceholderValue() != null && !req.getPlaceholderValue().isBlank()) {
            fd.setPlaceholderValue(req.getPlaceholderValue());
        } else if (fd.getPlaceholderValue() == null || fd.getPlaceholderValue().isBlank()) {
            // BRD import / bulk import rarely supplies one — backfill with a real
            // sample where one is knowable (binary and "reserved use" fields
            // intentionally stay blank — see generatePlaceholder()).
            fd.setPlaceholderValue(generatePlaceholder(fd.getDataType(), fd.getMaxLength(),
                    fd.getIsLlvar(), fd.getIsLllvar(), fd.getDeNumber(), fd.getFieldName()));
        }
        if (req.getDisplayOrder() != null) fd.setDisplayOrder(req.getDisplayOrder());
        if (req.getIsBuilderVisible() != null) fd.setIsBuilderVisible(req.getIsBuilderVisible());
        if (req.getIsActive() != null) fd.setActive(req.getIsActive());
        if (req.getDescription() != null) fd.setDescription(req.getDescription());
        fd.setUpdatedBy(UserContext.getUsername());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PLACEHOLDER GENERATION — every creation/update path funnels through
    // here so PLACEHOLDER is never blank in the Field Definitions grid.
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Real, standard ISO 8583 sample values keyed by DE number (digits only,
     * "DE49" and "49" both normalize to "49"). These are the values an
     * operations analyst would actually recognize — a currency code, a
     * transmission timestamp, a terminal ID — not generic filler text.
     * Anything not in this map falls back to generatePlaceholder() below.
     */
    private static final Map<String, String> KNOWN_PLACEHOLDERS = Map.ofEntries(
            Map.entry("2",  "4111111111111111"),      // PAN
            Map.entry("3",  "000000"),                 // Processing code
            Map.entry("4",  "000000010000"),            // Amount, transaction
            Map.entry("5",  "000000010000"),            // Amount, settlement
            Map.entry("6",  "000000010000"),            // Amount, cardholder billing
            Map.entry("7",  "0901143000"),               // Transmission date/time MMDDhhmmss
            Map.entry("9",  "00000000"),                 // Conversion rate, settlement
            Map.entry("10", "00000000"),                 // Conversion rate, cardholder billing
            Map.entry("11", "000001"),                    // STAN
            Map.entry("12", "143000"),                    // Local transaction time
            Map.entry("13", "0901"),                       // Local transaction date
            Map.entry("14", "2612"),                        // Expiration date YYMM
            Map.entry("15", "0902"),                         // Settlement date
            Map.entry("18", "5411"),                          // Merchant category code
            Map.entry("22", "051"),                            // POS entry mode
            Map.entry("23", "001"),                             // Card sequence number
            Map.entry("25", "00"),                               // POS condition code
            Map.entry("32", "12345678"),                          // Acquiring institution ID
            Map.entry("35", "4111111111111111=26121011234567890"), // Track 2 data
            Map.entry("37", "RRN000000001"),                        // Retrieval reference number
            Map.entry("38", "AUTH01"),                               // Authorization ID response
            Map.entry("39", "00"),                                    // Response code
            Map.entry("41", "TERM0001"),                               // Terminal ID
            Map.entry("42", "MERCHANT000001"),                         // Merchant ID
            Map.entry("43", "CITY MART/MUMBAI/IN"),                     // Card acceptor name/location
            Map.entry("44", "PARTIAL APPROVAL"),                        // Additional response data
            Map.entry("45", "%B4111111111111111^DOE/JOHN^26121010000000000000?"), // Track 1
            Map.entry("48", "ADDITIONAL DATA PRIVATE USE"),
            Map.entry("49", "356"),                                     // Currency code, transaction (INR)
            Map.entry("50", "356"),                                     // Currency code, settlement
            Map.entry("51", "356"),                                     // Currency code, cardholder billing
            Map.entry("54", "000000010000"),                            // Additional amounts
            Map.entry("70", "001"),                                     // Network management code
            Map.entry("90", "020000000001000000000002000001"),         // Original data elements
            Map.entry("100", "12345678"),                               // Receiving institution ID
            Map.entry("102", "1234567890123456"),                       // Account ID 1
            Map.entry("103", "1234567890123456")                        // Account ID 2
    );

    private static String resolvePlaceholder(String requested, DataType dataType, Integer maxLength,
                                             Boolean isLlvar, Boolean isLllvar, String deNumber, String fieldName) {
        if (requested != null && !requested.isBlank()) return requested;
        return generatePlaceholder(dataType, maxLength, isLlvar, isLllvar, deNumber, fieldName);
    }

    /**
     * Produces a representative sample value for a field so PLACEHOLDER is
     * never a bare "-" in the grid when a real one is knowable. Two cases
     * intentionally stay blank instead of showing a fake value:
     *   - Binary fields (bitmaps, PIN blocks) — system-generated at
     *     message-build time, not user-entered.
     *   - Fields whose name says "reserved for ISO/national/private use" —
     *     the ISO 8583 standard deliberately leaves these undefined, so
     *     there's no such thing as a "correct" sample; every switch fills
     *     them differently, and inventing one would just be fiction wearing
     *     a placeholder's clothes.
     *
     * Known standard DEs (see KNOWN_PLACEHOLDERS) get a real, recognizable
     * ISO 8583 sample value, clipped to this field's own max_length in case
     * a profile defines a shorter/longer field than the standard. Anything
     * else with an actual, definable purpose falls back to the field's own
     * name (e.g. "Additional Data - ISO" → "ADDITIONALDATAISO") rather than
     * generic filler text — numeric fields are the one exception, since
     * letters can't go in a numeric field, so they stay zero-padded.
     */
    private static String generatePlaceholder(DataType dataType, Integer maxLength,
                                              Boolean isLlvar, Boolean isLllvar, String deNumber, String fieldName) {
        if (dataType == null || dataType == DataType.binary) return null;
        if (isReservedUse(fieldName)) return null;

        int len = (maxLength != null && maxLength > 0) ? maxLength : 6;
        // LLVAR/LLLVAR carry a *maximum* length, not a fixed one — a shorter
        // representative sample reads better than padding out to the ceiling.
        boolean variableLength = Boolean.TRUE.equals(isLlvar) || Boolean.TRUE.equals(isLllvar);
        int cappedLen = variableLength ? Math.min(len, 10) : len;

        String known = deNumber != null
                ? KNOWN_PLACEHOLDERS.get(deNumber.replaceAll("\\D", ""))
                : null;
        if (known != null) {
            // Real values are already realistic full-length samples — only clip
            // if this profile's max_length is shorter than the standard sample.
            return known.length() > len ? known.substring(0, len) : known;
        }

        return switch (dataType) {
            case numeric -> "0".repeat(Math.max(cappedLen - 1, 0)) + "1"; // letters can't go in a numeric field
            case alpha -> placeholderFromFieldName(fieldName, deNumber, cappedLen, 'X');
            case alphanumeric -> placeholderFromFieldName(fieldName, deNumber, cappedLen, '0');
            case special -> "#".repeat(cappedLen);
            default -> null; // unreachable — binary handled above
        };
    }

    /**
     * Derives a placeholder from the field's own name — like MERCHANT000001
     * for DE42: a recognizable word, zero-padded to fill the length. Builds
     * up whole words ("ADDITIONAL", then "RESPONSE" if it still fits) and
     * stops BEFORE cutting a word in half, then pads the remainder — never
     * a mid-word fragment like the old "ADITIONALR". Falls back to the DE
     * number only if the field has no usable name at all.
     */
    private static String placeholderFromFieldName(String fieldName, String deNumber, int len, char pad) {
        String[] words = fieldName != null
                ? fieldName.toUpperCase().split("[^A-Z0-9]+")
                : new String[0];

        StringBuilder base = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (base.length() + word.length() <= len) {
                base.append(word);
            } else if (base.isEmpty()) {
                // Even the first word alone is longer than the field —
                // nothing else will fit better, so just clip it.
                base.append(word, 0, len);
            }
            if (base.length() >= len) break;
        }

        if (base.isEmpty()) {
            base.append(deNumber != null ? "DE" + deNumber.replaceAll("\\D", "") : "FIELD");
        }

        return padTo(base.toString(), len, pad);
    }

    /** Matches field names like "Reserved for ISO Use", "Reserved National", "Reserved Private". */
    private static boolean isReservedUse(String fieldName) {
        return fieldName != null && fieldName.toUpperCase().contains("RESERVED");
    }

    private static String padTo(String base, int len, char pad) {
        if (base.length() >= len) return base.substring(0, len);
        StringBuilder sb = new StringBuilder(base);
        while (sb.length() < len) sb.append(pad);
        return sb.toString();
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

    public FieldDefinitionDto toDto(FieldDefinition fd) {
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
        List<FieldDefinition> defs = fieldDefinitionRepository.findByProfileIdAndMtiAndDeletedAtIsNullOrderByDisplayOrderAsc(profileId, mti);
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