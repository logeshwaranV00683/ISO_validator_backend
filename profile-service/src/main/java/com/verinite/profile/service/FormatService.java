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

    private final MessageFormatRepository formatRepo;
    private final MessageFormatVersionRepository formatVersionRepo;
    private final SwitchProfileRepository profileRepo;
    private final FormatEventPublisher formatEventPublisher;
    private final AuditEventPublisher auditPublisher;


    private final PackagerXmlFieldParser packagerXmlFieldParser;
    private final RulesServiceClient rulesServiceClient;

    // ─────────────────────────────────────────────────────────────────────
    // CREATE
    // ─────────────────────────────────────────────────────────────────────

    @Transactional
    public FormatDto create(CreateFormatRequest req, String username) {

        // Guard 1 — profile must exist
        SwitchProfile profile = profileRepo.findByIdAndDeletedAtIsNull(req.getProfileId()).orElseThrow(() -> new RuntimeException("Profile not found: " + req.getProfileId()));

        // Guard 2 — format name unique within profile
        if (formatRepo.existsByProfileIdAndFormatNameAndDeletedAtIsNull(req.getProfileId(), req.getFormatName())) {

            throw new RuntimeException("Format name already exists for this profile: " + req.getFormatName());
        }

        String cleanedXml = preprocessXml(req.getXmlContent());
        String checksum = computeChecksum(cleanedXml);

        boolean validatedOk = false;
        LocalDateTime validatedAt = null;

        try {
            loadPackager(cleanedXml);
            validatedOk = true;
            validatedAt = LocalDateTime.now();

        } catch (Exception e) {
            log.warn("XML pre-validation failed for new format [{}]: {}", req.getFormatName(), e.getMessage());
        }

        String res=req.getMti();
        char[] ch=res.toCharArray();
        StringBuilder mti= new StringBuilder();
        for (char c : ch) {
            if (Character.isDigit(c)) {
                mti.append(c);
            }
            else throw new RuntimeException(" MTI can only accept digits...");
        }

        MessageFormat format = MessageFormat.builder().profileId(req.getProfileId()).formatName(req.getFormatName()).isoVersion(req.getIsoVersion()).encoding(req.getEncoding() != null ? req.getEncoding() : MessageFormat.Encoding.ASCII).mti(String.valueOf(mti)).totalFields(req.getTotalFields() != null ? req.getTotalFields() : 128).status(MessageFormat.Status.active).xmlContent(cleanedXml).checksum(checksum).currentVersion(1).description(req.getDescription()).createdBy(username).updatedBy(username).build();

        MessageFormat saved = formatRepo.save(format);

        // Snapshot version 1 into history so rollback works from day one
        formatVersionRepo.save(MessageFormatVersion.builder().formatId(saved.getId()).versionNumber(1).xmlContent(cleanedXml).checksum(checksum).changeNote("Initial version").isCurrent(true).validatedOk(validatedOk).validatedAt(validatedAt).createdBy(username).build());

        syncXmlFieldsToRulesService(saved.getProfileId(), profile.getProfileName(), saved.getMti(), cleanedXml);

        auditPublisher.publish(AuditEventPublisher.AuditEvent.builder().action("CREATE").entityType("FORMAT").entityId(saved.getId()).entityName(saved.getFormatName()).username(username).description("Format created for profileId=" + req.getProfileId()).build());

        log.info("Created format id={} profileId={} mti={} v1", saved.getId(), saved.getProfileId(), saved.getMti());

        return mapToDto(saved);
    }

    // ─────────────────────────────────────────────────────────────────────
    // READ
    // ─────────────────────────────────────────────────────────────────────

    public FormatDto getById(Long id) {
        return mapToDto(findOrThrow(id));
    }

    public List<FormatDto> getAll(boolean includeInactiveProfileFormats) {
        return formatRepo.findAllByDeletedAtIsNull().stream().filter(f -> includeInactiveProfileFormats || f.getProfile() == null || Boolean.TRUE.equals(f.getProfile().getActive())).map(this::mapToDto).collect(Collectors.toList());
    }


    public List<String> getMtisForProfile(Long profileId, boolean isAdmin) {

        if (!isAdmin) {
            SwitchProfile profile = profileRepo.findById(profileId).orElse(null);

            if (profile == null || !Boolean.TRUE.equals(profile.getActive())) {
                return List.of();
            }
        }

        return formatRepo.findDistinctMtiByProfileId(profileId);
    }

    // ─────────────────────────────────────────────────────────────────────
    // UPDATE
    // ─────────────────────────────────────────────────────────────────────

    @Transactional
    public FormatDto update(Long id, UpdateFormatRequest req, String username) {

        MessageFormat format = findOrThrow(id);

        boolean xmlChanged = req.getXmlContent() != null && !req.getXmlContent().isBlank();

        String oldMti = format.getMti();

        String newMti = req.getMti() != null && !req.getMti().isBlank() ? req.getMti() : format.getMti();

        SwitchProfile profile = profileRepo.findByIdAndDeletedAtIsNull(format.getProfileId()).orElseThrow(() -> new RuntimeException("Profile not found: " + format.getProfileId()));

        if (xmlChanged) {

            String cleanedXml = preprocessXml(req.getXmlContent());
            String checksum = computeChecksum(cleanedXml);
            int nextVer = format.getCurrentVersion() + 1;

            // Pre-validate new XML — non-blocking
            boolean validatedOk = false;
            LocalDateTime validatedAt = null;

            try {
                loadPackager(cleanedXml);
                validatedOk = true;
                validatedAt = LocalDateTime.now();

            } catch (Exception e) {
                log.warn("XML pre-validation failed for format id={}: {}", id, e.getMessage());
            }

            formatVersionRepo.clearCurrentForFormat(id);

            formatVersionRepo.save(MessageFormatVersion.builder().formatId(id).versionNumber(nextVer).xmlContent(cleanedXml).checksum(checksum).changeNote(req.getChangeNote()).isCurrent(true).validatedOk(validatedOk).validatedAt(validatedAt).createdBy(username).build());

            format.setXmlContent(cleanedXml);
            format.setChecksum(checksum);
            format.setCurrentVersion(nextVer);

            if (oldMti != null && !oldMti.isBlank() && newMti != null && !oldMti.equals(newMti)) {

                deleteRulesForFormat(format.getProfileId(), oldMti);

                log.info("Removed old rules/field definitions for profileId={} oldMti={}", format.getProfileId(), oldMti);
            }


            syncXmlFieldsToRulesService(format.getProfileId(), profile.getProfileName(), newMti, cleanedXml);

            formatEventPublisher.publishFormatUpdated(format.getProfileId(), id);

            log.info("Updated XML for format id={}, new version={}, mti={}", id, nextVer, newMti);
        }

        if (req.getFormatName() != null) {
            format.setFormatName(req.getFormatName());
        }

        if (req.getIsoVersion() != null) {
            format.setIsoVersion(req.getIsoVersion());
        }

        if (req.getEncoding() != null) {
            format.setEncoding(req.getEncoding());
        }

        if (req.getMti() != null) {
            format.setMti(req.getMti());
        }

        if (req.getTotalFields() != null) {
            format.setTotalFields(req.getTotalFields());
        }

        if (req.getDescription() != null) {
            format.setDescription(req.getDescription());
        }

        format.setUpdatedBy(username);

        MessageFormat saved = formatRepo.save(format);

        auditPublisher.publish(AuditEventPublisher.AuditEvent.builder().action("UPDATE").entityType("FORMAT").entityId(id).entityName(saved.getFormatName()).username(username).description(xmlChanged ? "XML updated to version " + saved.getCurrentVersion() : "Metadata updated").build());

        return mapToDto(saved);
    }

    // ─────────────────────────────────────────────────────────────────────
    // ROLLBACK
    // ─────────────────────────────────────────────────────────────────────

    @Transactional
    public FormatDto rollback(Long id, String username) {

        MessageFormat format = findOrThrow(id);

        int target = format.getCurrentVersion() - 1;

        if (target < 1) {
            throw new RuntimeException("No previous version available to roll back to");
        }

        return doRollback(format, target, username);
    }

    /**
     * PUT /formats/{id}/rollback/{version}
     * <p>
     * Rolls back to a specific historical version.
     */
    @Transactional
    public FormatDto rollbackToVersion(Long id, Integer targetVersionNum, String username) {

        MessageFormat format = findOrThrow(id);

        int target = targetVersionNum != null ? targetVersionNum : format.getCurrentVersion() - 1;

        if (target < 1) {
            throw new RuntimeException("No previous version available to roll back to");
        }

        return doRollback(format, target, username);
    }

    private FormatDto doRollback(MessageFormat format, int targetVersionNum, String username) {

        Long id = format.getId();

        MessageFormatVersion target = formatVersionRepo.findByFormatIdAndVersionNumber(id, targetVersionNum).orElseThrow(() -> new RuntimeException("Version " + targetVersionNum + " not found for format " + id));

        String restoredXml = preprocessXml(target.getXmlContent());
        String checksum = computeChecksum(restoredXml);
        int nextVer = format.getCurrentVersion() + 1;

        // Pre-validate restored XML — non-blocking
        boolean validatedOk = false;
        LocalDateTime validatedAt = null;

        try {
            loadPackager(restoredXml);
            validatedOk = true;
            validatedAt = LocalDateTime.now();

        } catch (Exception e) {
            log.warn("Rollback XML validation failed for format id={}: {}", id, e.getMessage());
        }

        SwitchProfile profile = profileRepo.findByIdAndDeletedAtIsNull(format.getProfileId()).orElseThrow(() -> new RuntimeException("Profile not found: " + format.getProfileId()));


        syncXmlFieldsToRulesService(format.getProfileId(), profile.getProfileName(), format.getMti(), restoredXml);

        // Retire current version
        formatVersionRepo.clearCurrentForFormat(id);

        // Create a new rollback version
        formatVersionRepo.save(MessageFormatVersion.builder().formatId(id).versionNumber(nextVer).xmlContent(restoredXml).checksum(checksum).changeNote("Rolled back to v" + targetVersionNum).isCurrent(true).validatedOk(validatedOk).validatedAt(validatedAt).createdBy(username).build());

        format.setXmlContent(restoredXml);
        format.setChecksum(checksum);
        format.setCurrentVersion(nextVer);
        format.setUpdatedBy(username);

        formatRepo.save(format);

        formatEventPublisher.publishFormatRolledBack(format.getProfileId(), id);

        log.info("Rolled back format id={} to content of v{}, new version={}", id, targetVersionNum, nextVer);

        return mapToDto(format);
    }

    // ─────────────────────────────────────────────────────────────────────
    // VERSION HISTORY
    // ─────────────────────────────────────────────────────────────────────

    public List<FormatVersionDto> getVersions(Long id) {

        findOrThrow(id);

        return formatVersionRepo.findAllByFormatIdOrderByVersionNumberDesc(id).stream().map(v -> FormatVersionDto.builder().id(v.getId()).formatId(v.getFormatId()).versionNumber(v.getVersionNumber()).checksum(v.getChecksum()).changeNote(v.getChangeNote()).isCurrent(v.getIsCurrent()).validatedOk(v.getValidatedOk()).validatedAt(v.getValidatedAt()).createdAt(v.getCreatedAt()).createdBy(v.getCreatedBy()).build()).collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────
    // INTERNAL — used by validation-engine
    // ─────────────────────────────────────────────────────────────────────

    public ProfileFormatResponse getActiveFormatByProfile(Long profileId) {

        List<MessageFormat> active = formatRepo.findAllByProfileIdAndStatusAndDeletedAtIsNull(profileId, MessageFormat.Status.active);

        if (active.isEmpty()) {
            throw new RuntimeException("No active format found for profile: " + profileId);
        }

        // First active format wins
        MessageFormat format = active.get(0);

        String profileName = profileRepo.findByIdAndDeletedAtIsNull(profileId).map(SwitchProfile::getProfileName).orElse(null);

        return ProfileFormatResponse.builder().formatId(format.getId()).xmlContent(format.getXmlContent()).mti(format.getMti()).profileId(format.getProfileId()).profileName(profileName).build();
    }

    // ─────────────────────────────────────────────────────────────────────
    // STATUS / SOFT DELETE
    // ─────────────────────────────────────────────────────────────────────

    @Transactional
    public void setActive(Long id, boolean active, String username) {

        MessageFormat format = findOrThrow(id);

        format.setStatus(active ? MessageFormat.Status.active : MessageFormat.Status.inactive);

        format.setUpdatedBy(username);
        formatRepo.save(format);

        if (active) {
            formatEventPublisher.publishFormatUpdated(format.getProfileId(), id);
        }

        auditPublisher.publish(AuditEventPublisher.AuditEvent.builder().action("SET_STATUS").entityType("FORMAT").entityId(id).entityName(format.getFormatName()).username(username).description("Format status set to " + (active ? "active" : "inactive")).build());

        log.info("Format id={} status={}", id, active ? "active" : "inactive");
    }

    @Transactional
    public void delete(Long id, String username) {

        MessageFormat format = findOrThrow(id);

        format.setDeletedAt(LocalDateTime.now());
        format.setUpdatedBy(username);

        formatRepo.save(format);

        auditPublisher.publish(AuditEventPublisher.AuditEvent.builder().action("DELETE").entityType("FORMAT").entityId(id).entityName(format.getFormatName()).username(username).description("Format soft-deleted").build());

        log.info("Soft-deleted format id={}", id);
    }

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

    private void syncXmlFieldsToRulesService(Long profileId, String profileName, String mti, String xmlContent) {

        if (profileId == null) {
            throw new IllegalArgumentException("Cannot synchronize XML fields: profileId is null");
        }

        if (profileName == null || profileName.isBlank()) {
            throw new IllegalArgumentException("Cannot synchronize XML fields: profileName is blank");
        }

        if (mti == null || mti.isBlank()) {
            throw new IllegalArgumentException("Cannot synchronize XML fields: MTI is required");
        }

        if (mti.length() != 4) {
            throw new IllegalArgumentException("Cannot synchronize XML fields: MTI must be exactly 4 characters: " + mti);
        }

        if (xmlContent == null || xmlContent.isBlank()) {
            throw new IllegalArgumentException("Cannot synchronize XML fields: XML content is empty");
        }

        List<PackagerXmlFieldParser.ParsedField> parsedFields = packagerXmlFieldParser.parse(xmlContent);

        if (parsedFields.isEmpty()) {
            log.warn("No data element fields found in XML for profileId={} mti={}", profileId, mti);

            return;
        }


        List<BulkImportFieldDefsRequest.FieldDefItem> definitions = new java.util.ArrayList<>();

        for (int i = 0; i < parsedFields.size(); i++) {

            PackagerXmlFieldParser.ParsedField field = parsedFields.get(i);

            String dataType = packagerXmlFieldParser.resolveDataType(field.getIsoClass());

            boolean isLlvar = packagerXmlFieldParser.isLlvar(field.getIsoClass());

            boolean isLllvar = packagerXmlFieldParser.isLllvar(field.getIsoClass());

            Integer maxLength = field.getLength();

            if (maxLength == null) {
                log.warn("Skipping XML field DE{} because length is missing. " + "profileId={} mti={} class={}", field.getDeNumber(), profileId, mti, field.getIsoClass());
                continue;
            }

            definitions.add(BulkImportFieldDefsRequest.FieldDefItem.builder().profileId(profileId).profileName(profileName).mti(mti).deNumber(field.getDeNumber()).fieldName(field.getFieldName()).dataType(dataType).maxLength(maxLength).isLlvar(isLlvar).isLllvar(isLllvar).isMandatory(false)

                    .displayOrder(i).isBuilderVisible(true).isActive(true).description(buildXmlFieldDescription(field)).build());
        }

        if (definitions.isEmpty()) {
            log.warn("XML contained no usable field definitions for " + "profileId={} mti={}", profileId, mti);
            return;
        }

        BulkImportFieldDefsRequest fieldRequest = BulkImportFieldDefsRequest.builder().profileId(profileId).profileName(profileName).mti(mti).strategy("REPLACE").definitions(definitions).build();

        BulkImportResultDto fieldResult = rulesServiceClient.bulkImportFieldDefinitions(fieldRequest);

        log.info("XML field-definition sync completed: " + "profileId={} mti={} imported={} updated={} skipped={}", profileId, mti, fieldResult != null ? fieldResult.getImported() : null, fieldResult != null ? fieldResult.getUpdated() : null, fieldResult != null ? fieldResult.getSkipped() : null);

        List<BulkImportRulesRequest.RuleItem> rules = new java.util.ArrayList<>();

        for (int i = 0; i < parsedFields.size(); i++) {

            PackagerXmlFieldParser.ParsedField field = parsedFields.get(i);

            Integer maxLength = field.getLength();

            if (maxLength == null) {
                continue;
            }

            String dataType = packagerXmlFieldParser.resolveDataType(field.getIsoClass());

            rules.add(BulkImportRulesRequest.RuleItem.builder().profileId(profileId).profileName(profileName).mti(mti).deNumber(field.getDeNumber()).fieldName(field.getFieldName()).isMandatory(false).maxLength(maxLength).dataType(dataType)
                    .severity("CRITICAL").priority(i + 1).isActive(true).description(buildXmlFieldDescription(field)).build());
        }

        if (!rules.isEmpty()) {

            BulkImportRulesRequest rulesRequest = BulkImportRulesRequest.builder().profileId(profileId).profileName(profileName).mti(mti).strategy("REPLACE").rules(rules).build();

            BulkImportResultDto ruleResult = rulesServiceClient.bulkImportRules(rulesRequest);

            log.info("XML rule sync completed: " + "profileId={} mti={} imported={} updated={} skipped={}", profileId, mti, ruleResult != null ? ruleResult.getImported() : null, ruleResult != null ? ruleResult.getUpdated() : null, ruleResult != null ? ruleResult.getSkipped() : null);
        }

        log.info("XML → Rules Manager synchronization completed: " + "profileId={} mti={} fields={}", profileId, mti, definitions.size());
    }


    private void deleteRulesForFormat(Long profileId, String mti) {

        if (profileId == null || mti == null || mti.isBlank()) {
            return;
        }

        try {
            Integer deletedFields = rulesServiceClient.deleteFieldDefinitionsForFormat(profileId, mti);

            Integer deletedRules = rulesServiceClient.deleteRulesForFormat(profileId, mti);

            log.info("Removed rules-service configuration for " + "profileId={} mti={}: fields={}, rules={}", profileId, mti, deletedFields, deletedRules);

        } catch (Exception e) {


            log.error("Failed to remove old Rules Manager configuration " + "for profileId={} mti={}: {}", profileId, mti, e.getMessage(), e);

            throw new RuntimeException("Failed to synchronize old MTI configuration " + profileId + "/" + mti, e);
        }
    }


    private String buildXmlFieldDescription(PackagerXmlFieldParser.ParsedField field) {

        StringBuilder description = new StringBuilder("Imported from jPOS packager XML");

        if (field.getIsoClass() != null && !field.getIsoClass().isBlank()) {

            description.append("; class=").append(field.getIsoClass());
        }

        if (field.getLength() != null) {

            description.append("; length=").append(field.getLength());
        }

        return description.toString();
    }

    // ─────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────

    private MessageFormat findOrThrow(Long id) {

        return formatRepo.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new RuntimeException("Format not found: " + id));
    }

    public FormatDto mapToDto(MessageFormat f) {

        return FormatDto.builder().id(f.getId()).profileId(f.getProfileId()).formatName(f.getFormatName()).isoVersion(f.getIsoVersion()).encoding(f.getEncoding()).mti(f.getMti()).totalFields(f.getTotalFields()).status(f.getStatus()).xmlContent(f.getXmlContent()).checksum(f.getChecksum()).currentVersion(f.getCurrentVersion()).description(f.getDescription()).createdBy(f.getCreatedBy()).updatedBy(f.getUpdatedBy()).createdAt(f.getCreatedAt()).updatedAt(f.getUpdatedAt()).build();
    }

    /**
     * Strips BOM, normalizes line endings, and ensures XML declaration.
     */
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

    /**
     * Loads jPOS GenericPackager — throws if XML is invalid.
     */
    private void loadPackager(String cleanXml) throws Exception {

        new org.jpos.iso.packager.GenericPackager(new ByteArrayInputStream(cleanXml.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * SHA-256 hex digest.
     */
    private String computeChecksum(String content) {

        try {

            byte[] hash = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder(64);

            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }

            return sb.toString();

        } catch (NoSuchAlgorithmException e) {

            log.warn("SHA-256 unavailable — checksum not computed");

            return null;
        }
    }
}