package com.verinite.profile.service;

import com.verinite.profile.client.RulesServiceClient;
import com.verinite.profile.dto.*;
import com.verinite.profile.entity.MessageFormat;
import com.verinite.profile.entity.MessageFormatVersion;
import com.verinite.profile.entity.SwitchProfile;
import com.verinite.profile.event.AuditEventPublisher;
import com.verinite.profile.event.FormatEventPublisher;
import com.verinite.profile.repository.MessageFormatRepository;
import com.verinite.profile.repository.MessageFormatVersionRepository;
import com.verinite.profile.repository.SwitchProfileRepository;
import com.verinite.profile.util.PackagerXmlFieldParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FormatService {

    private final MessageFormatRepository        formatRepo;
    private final MessageFormatVersionRepository formatVersionRepo;
    private final SwitchProfileRepository        profileRepo;
    private final FormatEventPublisher           formatEventPublisher;
    private final AuditEventPublisher            auditPublisher;
    private final RulesServiceClient rulesServiceClient;
    private final PackagerXmlFieldParser packagerXmlFieldParser;

    // ─────────────────────────────────────────────────────────────────────
    // CREATE
    // ─────────────────────────────────────────────────────────────────────

    @Transactional
    public FormatDto create(CreateFormatRequest req, String username) {

        // Guard 1 — profile must exist
        profileRepo.findByIdAndDeletedAtIsNull(req.getProfileId())
                .orElseThrow(() -> new RuntimeException(
                        "Profile not found: " + req.getProfileId()));

        // Guard 2 — format name unique within profile
        if (formatRepo.existsByProfileIdAndFormatNameAndDeletedAtIsNull(
                req.getProfileId(), req.getFormatName())) {
            throw new RuntimeException(
                    "Format name already exists for this profile: " + req.getFormatName());
        }

        String cleanedXml = preprocessXml(req.getXmlContent());
        String checksum   = computeChecksum(cleanedXml);

        // Pre-validate XML via jPOS packager — non-blocking (warn, don't reject)
        boolean   validatedOk = false;
        LocalDateTime validatedAt = null;
        try {
            loadPackager(cleanedXml);
            validatedOk = true;
            validatedAt = LocalDateTime.now();
        } catch (Exception e) {
            log.warn("XML pre-validation failed for new format [{}]: {}",
                    req.getFormatName(), e.getMessage());
        }

        MessageFormat format = MessageFormat.builder()
                .profileId(req.getProfileId())
                .formatName(req.getFormatName())
                .isoVersion(req.getIsoVersion())
                .encoding(req.getEncoding() != null ? req.getEncoding() : MessageFormat.Encoding.ASCII)
                .mti(req.getMti())
                .totalFields(req.getTotalFields() != null ? req.getTotalFields() : 128)
                .status(MessageFormat.Status.active)
                .xmlContent(cleanedXml)
                .checksum(checksum)
                .currentVersion(1)
                .description(req.getDescription())
                .createdBy(username)
                .updatedBy(username)
                .build();

        MessageFormat saved = formatRepo.save(format);

        // Snapshot version 1 into history so rollback works from day one
        formatVersionRepo.save(MessageFormatVersion.builder()
                .formatId(saved.getId())
                .versionNumber(1)
                .xmlContent(cleanedXml)
                .checksum(checksum)
                .changeNote("Initial version")
                .isCurrent(true)
                .validatedOk(validatedOk)
                .validatedAt(validatedAt)
                .createdBy(username)
                .build());

        auditPublisher.publish(AuditEventPublisher.AuditEvent.builder()
                .action("CREATE")
                .entityType("FORMAT")
                .entityId(saved.getId())
                .entityName(saved.getFormatName())
                .username(username)
                .description("Format created for profileId=" + req.getProfileId())
                .build());

        log.info("Created format id={} profileId={} v1", saved.getId(), saved.getProfileId());
        syncFieldDefinitionsAndRules(saved, req.getMti());
        return mapToDto(saved);
    }

    // ─────────────────────────────────────────────────────────────────────
    // READ
    // ─────────────────────────────────────────────────────────────────────

    public FormatDto getById(Long id) {
        return mapToDto(findOrThrow(id));
    }

    public List<FormatDto> getAll() {
        return formatRepo.findAllByDeletedAtIsNull()
                .stream().map(this::mapToDto).collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────
    // UPDATE (XML + metadata)
    // ─────────────────────────────────────────────────────────────────────

    @Transactional
    public FormatDto update(Long id, UpdateFormatRequest req, String username) {
        MessageFormat format = findOrThrow(id);

        boolean xmlChanged = req.getXmlContent() != null && !req.getXmlContent().isBlank();

        if (xmlChanged) {
            String cleanedXml = preprocessXml(req.getXmlContent());
            String checksum   = computeChecksum(cleanedXml);
            int    nextVer    = format.getCurrentVersion() + 1;

            // Pre-validate new XML — non-blocking
            boolean   validatedOk = false;
            LocalDateTime validatedAt = null;
            try {
                loadPackager(cleanedXml);
                validatedOk = true;
                validatedAt = LocalDateTime.now();
            } catch (Exception e) {
                log.warn("XML pre-validation failed for format id={}: {}", id, e.getMessage());
            }

            // Retire current is_current flag before creating the new version
            formatVersionRepo.clearCurrentForFormat(id);

            // Snapshot new version into history
            formatVersionRepo.save(MessageFormatVersion.builder()
                    .formatId(id)
                    .versionNumber(nextVer)
                    .xmlContent(cleanedXml)
                    .checksum(checksum)
                    .changeNote(req.getChangeNote())
                    .isCurrent(true)
                    .validatedOk(validatedOk)
                    .validatedAt(validatedAt)
                    .createdBy(username)
                    .build());

            format.setXmlContent(cleanedXml);
            format.setChecksum(checksum);
            format.setCurrentVersion(nextVer);

            formatEventPublisher.publishFormatUpdated(format.getProfileId(), id);
            log.info("Updated XML for format id={}, new version={}", id, nextVer);
            syncFieldDefinitionsAndRules(format, format.getMti());
        }

        // Metadata — apply regardless of whether XML changed
        if (req.getFormatName()  != null) format.setFormatName(req.getFormatName());
        if (req.getIsoVersion()  != null) format.setIsoVersion(req.getIsoVersion());
        if (req.getEncoding()    != null) format.setEncoding(req.getEncoding());
        if (req.getMti()         != null) format.setMti(req.getMti());
        if (req.getTotalFields() != null) format.setTotalFields(req.getTotalFields());
        if (req.getDescription() != null) format.setDescription(req.getDescription());
        format.setUpdatedBy(username);

        MessageFormat saved = formatRepo.save(format);

        auditPublisher.publish(AuditEventPublisher.AuditEvent.builder()
                .action("UPDATE")
                .entityType("FORMAT")
                .entityId(id)
                .entityName(saved.getFormatName())
                .username(username)
                .description(xmlChanged
                        ? "XML updated to version " + saved.getCurrentVersion()
                        : "Metadata updated")
                .build());

        return mapToDto(saved);
    }

    // ─────────────────────────────────────────────────────────────────────
    // ROLLBACK
    // ─────────────────────────────────────────────────────────────────────

    /** PUT /formats/{id}/rollback — rolls back to currentVersion - 1 */
    @Transactional
    public FormatDto rollback(Long id, String username) {
        MessageFormat format = findOrThrow(id);
        int target = format.getCurrentVersion() - 1;
        if (target < 1) {
            throw new RuntimeException("No previous version available to roll back to");
        }
        return doRollback(format, target, username);
    }

    /** PUT /formats/{id}/rollback/{version} — rolls back to a specific version */
    @Transactional
    public FormatDto rollbackToVersion(Long id, Integer targetVersionNum, String username) {
        MessageFormat format = findOrThrow(id);
        int target = (targetVersionNum != null) ? targetVersionNum : format.getCurrentVersion() - 1;
        if (target < 1) {
            throw new RuntimeException("No previous version available to roll back to");
        }
        return doRollback(format, target, username);
    }

    /**
     * Core rollback logic.
     * Rollback is always FORWARD — it creates a new version (currentVersion + 1)
     * whose content mirrors the target historical version.  The version history
     * is therefore append-only and fully auditable.
     */
    private FormatDto doRollback(MessageFormat format, int targetVersionNum, String username) {
        Long id = format.getId();

        MessageFormatVersion target = formatVersionRepo
                .findByFormatIdAndVersionNumber(id, targetVersionNum)
                .orElseThrow(() -> new RuntimeException(
                        "Version " + targetVersionNum + " not found for format " + id));

        String restoredXml = target.getXmlContent();
        String checksum    = computeChecksum(restoredXml);
        int    nextVer     = format.getCurrentVersion() + 1;

        // Pre-validate restored XML — non-blocking
        boolean   validatedOk = false;
        LocalDateTime validatedAt = null;
        try {
            loadPackager(preprocessXml(restoredXml));
            validatedOk = true;
            validatedAt = LocalDateTime.now();
        } catch (Exception e) {
            log.warn("Rollback XML validation failed for format id={}: {}", id, e.getMessage());
        }

        // Retire current is_current before creating the new rollback version
        formatVersionRepo.clearCurrentForFormat(id);

        formatVersionRepo.save(MessageFormatVersion.builder()
                .formatId(id)
                .versionNumber(nextVer)
                .xmlContent(restoredXml)
                .checksum(checksum)
                .changeNote("Rolled back to v" + targetVersionNum)
                .isCurrent(true)
                .validatedOk(validatedOk)
                .validatedAt(validatedAt)
                .createdBy(username)
                .build());

        format.setXmlContent(restoredXml);
        format.setChecksum(checksum);
        format.setCurrentVersion(nextVer);
        format.setUpdatedBy(username);
        formatRepo.save(format);

        formatEventPublisher.publishFormatRolledBack(format.getProfileId(), id);
        log.info("Rolled back format id={} to content of v{}, new version={}",
                id, targetVersionNum, nextVer);
        return mapToDto(format);
    }

    // ─────────────────────────────────────────────────────────────────────
    // VERSION HISTORY
    // ─────────────────────────────────────────────────────────────────────

    public List<FormatVersionDto> getVersions(Long id) {
        findOrThrow(id); // guard — fail fast if format not found
        return formatVersionRepo.findAllByFormatIdOrderByVersionNumberDesc(id)
                .stream()
                .map(v -> FormatVersionDto.builder()
                        .id(v.getId())
                        .formatId(v.getFormatId())
                        .versionNumber(v.getVersionNumber())
                        .checksum(v.getChecksum())
                        .changeNote(v.getChangeNote())
                        .isCurrent(v.getIsCurrent())
                        .validatedOk(v.getValidatedOk())
                        .validatedAt(v.getValidatedAt())
                        .createdAt(v.getCreatedAt())
                        .createdBy(v.getCreatedBy())
                        .build())
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────
    // INTERNAL — used by validation-engine via InternalProfileController
    // ─────────────────────────────────────────────────────────────────────

    public ProfileFormatResponse getActiveFormatByProfile(Long profileId) {
        List<MessageFormat> active = formatRepo
                .findAllByProfileIdAndStatusAndDeletedAtIsNull(
                        profileId, MessageFormat.Status.active);

        if (active.isEmpty()) {
            throw new RuntimeException("No active format found for profile: " + profileId);
        }

        // First active format wins; callers should ensure only one active format per profile
        MessageFormat format = active.get(0);

        String profileName = profileRepo.findByIdAndDeletedAtIsNull(profileId)
                .map(SwitchProfile::getProfileName)
                .orElse(null);

        return ProfileFormatResponse.builder()
                .formatId(format.getId())
                .xmlContent(format.getXmlContent())
                .mti(format.getMti())
                .profileId(format.getProfileId())
                .profileName(profileName)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────
    // STATUS / SOFT-DELETE
    // ─────────────────────────────────────────────────────────────────────

    @Transactional
    public void setActive(Long id, boolean active, String username) {
        MessageFormat format = findOrThrow(id);
        // FIX: was always setting Status.active regardless of the param
        format.setStatus(active ? MessageFormat.Status.active : MessageFormat.Status.inactive);
        format.setUpdatedBy(username);
        formatRepo.save(format);

        // Publish cache invalidation only when reactivating
        if (active) {
            formatEventPublisher.publishFormatUpdated(format.getProfileId(), id);
        }

        auditPublisher.publish(AuditEventPublisher.AuditEvent.builder()
                .action("SET_STATUS")
                .entityType("FORMAT")
                .entityId(id)
                .entityName(format.getFormatName())
                .username(username)
                .description("Format status set to " + (active ? "active" : "inactive"))
                .build());

        log.info("Format id={} status={}", id, active ? "active" : "inactive");
    }

    @Transactional
    public void delete(Long id, String username) {
        MessageFormat format = findOrThrow(id);
        format.setDeletedAt(LocalDateTime.now());
        format.setUpdatedBy(username);
        formatRepo.save(format);

        auditPublisher.publish(AuditEventPublisher.AuditEvent.builder()
                .action("DELETE")
                .entityType("FORMAT")
                .entityId(id)
                .entityName(format.getFormatName())
                .username(username)
                .description("Format soft-deleted")
                .build());

        log.info("Soft-deleted format id={}", id);

        // Clean up Rules Manager data — but only if no other active format
        // still uses this same profileId+mti (formats aren't unique per mti).
        cleanupRulesManagerData(format, id);
    }

    private void cleanupRulesManagerData(MessageFormat format, Long deletedFormatId) {
        String mti = normalizeMti(format.getMti());
        if (mti == null) return; // format never had a valid mti to sync in the first place

        boolean stillInUse = formatRepo.existsByProfileIdAndMtiAndIdNotAndDeletedAtIsNull(
                format.getProfileId(), mti, deletedFormatId);
        if (stillInUse) {
            log.info("[Format Delete] Skipping rules-manager cleanup — another active format still uses profileId={} mti={}",
                    format.getProfileId(), mti);
            return;
        }

        try {
            int fieldDefsDeleted = rulesServiceClient.deleteFieldDefinitionsForFormat(format.getProfileId(), mti);
            int rulesDeleted     = rulesServiceClient.deleteRulesForFormat(format.getProfileId(), mti);
            log.info("[Format Delete] Cleaned up rules-manager for profileId={} mti={} — fieldDefs={} rules={}",
                    format.getProfileId(), mti, fieldDefsDeleted, rulesDeleted);
        } catch (Exception e) {
            log.error("[Format Delete] Rules-manager cleanup failed for profileId={} mti={}: {}",
                    format.getProfileId(), mti, e.getMessage(), e);
        }
    }
    // ─────────────────────────────────────────────────────────────────────
    // XML VALIDATION (dry-run, no DB write)
    // ─────────────────────────────────────────────────────────────────────

    public void validateXml(String xmlContent) {
        try {
            loadPackager(preprocessXml(xmlContent));
            log.info("XML validation passed — packager loaded successfully");
        } catch (Exception e) {
            throw new RuntimeException("Invalid jPOS XML: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // CACHE RELOAD SIGNAL
    // ─────────────────────────────────────────────────────────────────────

    public void reload(Long id) {
        MessageFormat format = findOrThrow(id);
        formatEventPublisher.publishFormatUpdated(format.getProfileId(), id);
        log.info("Reload signal published for formatId={}", id);
    }

    // ─────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────

    private MessageFormat findOrThrow(Long id) {
        return formatRepo.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Format not found: " + id));
    }

    public FormatDto mapToDto(MessageFormat f) {
        return FormatDto.builder()
                .id(f.getId())
                .profileId(f.getProfileId())
                .formatName(f.getFormatName())
                .isoVersion(f.getIsoVersion())
                .encoding(f.getEncoding())
                .mti(f.getMti())
                .totalFields(f.getTotalFields())
                .status(f.getStatus())
                .xmlContent(f.getXmlContent())
                .checksum(f.getChecksum())
                .currentVersion(f.getCurrentVersion())
                .description(f.getDescription())
                .createdBy(f.getCreatedBy())
                .updatedBy(f.getUpdatedBy())
                .createdAt(f.getCreatedAt())
                .updatedAt(f.getUpdatedAt())
                .build();
    }

    /** Strips BOM, normalises line endings, ensures XML declaration. */
    private String preprocessXml(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new RuntimeException("xmlContent is null or blank");
        }
        String s = raw.startsWith("\uFEFF") ? raw.substring(1) : raw;
        s = s.strip().replace("\r\n", "\n").replace("\r", "\n");
        if (!s.startsWith("<?xml")) {
            s = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + s;
        }
        return s;
    }

    /** Loads jPOS GenericPackager — throws if XML is invalid. */
    private void loadPackager(String cleanXml) throws Exception {
        new org.jpos.iso.packager.GenericPackager(
                new ByteArrayInputStream(cleanXml.getBytes(StandardCharsets.UTF_8)));
    }

    /** SHA-256 hex digest of the given content. */
    private String computeChecksum(String content) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            log.warn("SHA-256 unavailable — checksum not computed");
            return null;
        }
    }


    private void syncFieldDefinitionsAndRules(MessageFormat format, String mti) {
        String normalizedMti = normalizeMti(mti);
        if (normalizedMti == null) {
            log.warn("[Format XML] Skipping rules-manager sync for formatId={} — missing/invalid MTI", format.getId());
            return;
        }

        try {
            List<PackagerXmlFieldParser.ParsedField> fields =
                    packagerXmlFieldParser.parse(format.getXmlContent());

            if (fields.isEmpty()) {
                log.info("[Format XML] No data element fields found in XML for formatId={} — nothing to sync", format.getId());
                return;
            }

            SwitchProfile profile = profileRepo.findByIdAndDeletedAtIsNull(format.getProfileId())
                    .orElse(null);
            String profileName = profile != null ? profile.getProfileName() : ("Profile " + format.getProfileId());

            // Field Definitions
            BulkImportFieldDefsRequest fieldDefsRequest = BulkImportFieldDefsRequest.builder()
                    .profileId(format.getProfileId())
                    .profileName(profileName)
                    .mti(normalizedMti)
                    .strategy("MERGE")
                    .definitions(fields.stream()
                            .map(f -> BulkImportFieldDefsRequest.FieldDefItem.builder()
                                    .profileId(format.getProfileId())
                                    .profileName(profileName)
                                    .mti(normalizedMti)
                                    .deNumber(f.getDeNumber())
                                    .fieldName(f.getFieldName())
                                    .dataType(packagerXmlFieldParser.resolveDataType(f.getIsoClass()))
                                    .maxLength(f.getLength() != null ? f.getLength() : 1)
                                    .isLlvar(packagerXmlFieldParser.isLlvar(f.getIsoClass()))
                                    .isLllvar(packagerXmlFieldParser.isLllvar(f.getIsoClass()))
                                    .isMandatory(false)
                                    .displayOrder(Integer.parseInt(f.getDeNumber()))
                                    .isBuilderVisible(true)
                                    .isActive(true)
                                    .description("Synced from format XML: " + format.getFormatName())
                                    .build())
                            .toList())
                    .build();
            BulkImportResultDto fieldDefsResult = rulesServiceClient.bulkImportFieldDefinitions(fieldDefsRequest);

            // Rules — basic auto-generated rules: length + data type only, not mandatory, WARNING severity
            BulkImportRulesRequest rulesRequest = BulkImportRulesRequest.builder()
                    .profileId(format.getProfileId())
                    .profileName(profileName)
                    .mti(normalizedMti)
                    .strategy("MERGE")
                    .rules(fields.stream()
                            .map(f -> BulkImportRulesRequest.RuleItem.builder()
                                    .profileId(format.getProfileId())
                                    .profileName(profileName)
                                    .mti(normalizedMti)
                                    .deNumber(f.getDeNumber())
                                    .fieldName(f.getFieldName())
                                    .isMandatory(false)
                                    .maxLength(f.getLength() != null ? f.getLength() : 1)
                                    .dataType(packagerXmlFieldParser.resolveDataType(f.getIsoClass()))
                                    .severity("WARNING")
                                    .priority(Integer.parseInt(f.getDeNumber()))
                                    .isActive(true)
                                    .description("Auto-generated from format XML: " + format.getFormatName())
                                    .build())
                            .toList())
                    .build();
            BulkImportResultDto rulesResult = rulesServiceClient.bulkImportRules(rulesRequest);

            log.info("[Format XML] Synced formatId={} mti={} fields={} → fieldDefs(imported={},updated={}) rules(imported={},updated={})",
                    format.getId(), normalizedMti, fields.size(),
                    fieldDefsResult.getImported(), fieldDefsResult.getUpdated(),
                    rulesResult.getImported(), rulesResult.getUpdated());

        } catch (Exception e) {
            log.error("[Format XML] Rules-manager sync failed for formatId={}: {}", format.getId(), e.getMessage(), e);
            auditPublisher.publish(AuditEventPublisher.AuditEvent.builder()
                    .action("SYNC_FAILED")
                    .entityType("FORMAT")
                    .entityId(format.getId())
                    .entityName(format.getFormatName())
                    .username(format.getUpdatedBy())
                    .description("Rules-manager sync failed: " + e.getMessage())
                    .build());
        }
    }

    private String normalizeMti(String mti) {
        if (mti == null) return null;
        String trimmed = mti.trim();
        return (trimmed.length() == 4 && trimmed.chars().allMatch(Character::isDigit)) ? trimmed : null;
    }

    public List<String> getMtisForProfile(Long profileId) {
        return formatRepo.findDistinctMtisByProfileId(profileId);
    }
}