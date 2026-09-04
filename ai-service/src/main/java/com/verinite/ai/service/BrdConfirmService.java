package com.verinite.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verinite.ai.client.ProfileServiceClient;
import com.verinite.ai.client.RulesServiceClient;
import com.verinite.ai.dto.*;
import com.verinite.ai.entity.BrdDocument;
import com.verinite.ai.entity.BrdEmbeddingChunk;
import com.verinite.ai.exception.NotFoundException;
import com.verinite.ai.repository.BrdDocumentRepository;
import com.verinite.ai.repository.BrdEmbeddingChunkRepository;
import com.verinite.common.dto.ApiResponse;
import com.verinite.common.enums.BrdExtractStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BrdConfirmService {

    private final BrdDocumentRepository       brdDocumentRepository;
    private final BrdEmbeddingChunkRepository brdEmbeddingChunkRepository;
    private final ProfileServiceClient        profileServiceClient;
    private final RulesServiceClient          rulesServiceClient;
    private final ObjectMapper                objectMapper;

    @Transactional
    public ConfirmResult confirm(Long brdDocumentId, String confirmedBy) {
        BrdDocument document = brdDocumentRepository.findById(brdDocumentId)
                .orElseThrow(() -> new NotFoundException("BRD document not found: " + brdDocumentId));

        if (document.getStatus() != BrdExtractStatus.COMPLETED) {
            throw new IllegalStateException(
                    "BRD document must be COMPLETED before confirm (current status: " + document.getStatus() + ")");
        }

        BrdExtractedConfig config = deserialize(document.getExtractedJson());
        String mti = normalizeMti(config.getMti());

        // 1) Create switch profile
        CreateProfileRequest profileRequest = CreateProfileRequest.builder()
                .profileName(config.getSwitchProfile() != null ? config.getSwitchProfile().getProfileName() : "BRD Import " + brdDocumentId)
                .description(config.getSwitchProfile() != null ? config.getSwitchProfile().getDescription() : "Imported from BRD document")
                .environment(config.getSwitchProfile() != null && config.getSwitchProfile().getEnvironment() != null
                        ? config.getSwitchProfile().getEnvironment() : "DEV")
                .host("localhost")
                .port(9100)
                .timezone("Etc/UTC")
                .connectionTimeoutMs(30000)
                .tpduEnabled(false)
                .isActive(true)
                .isDefault(false)
                .build();

        ApiResponse<ProfileDto> profileResponse = profileServiceClient.createProfile(profileRequest);
        ProfileDto profile = profileResponse.getData();
        Long profileId = profile.getId();
        String profileName = profile.getProfileName();

        // 2) Create format (jPOS packager XML) for the new profile
        String xmlContent = buildPackagerXml(config);
        CreateFormatRequest formatRequest = CreateFormatRequest.builder()
                .profileId(profileId)
                .formatName(profileName + " - BRD Format")
                .isoVersion("ISO 8583-1:1987")
                .encoding("ASCII")
                .mti(mti)
                .totalFields(128)
                .description("Auto-generated from BRD document #" + brdDocumentId)
                .xmlContent(xmlContent)
                .build();
        profileServiceClient.createFormat(formatRequest);

        // 3) Bulk import field definitions
        int fieldDefsImported = 0;
        if (config.getFieldDefinitions() != null && !config.getFieldDefinitions().isEmpty()) {
            BulkImportFieldDefsRequest fieldDefsRequest = BulkImportFieldDefsRequest.builder()
                    .profileId(profileId)
                    .profileName(profileName)
                    .mti(mti)
                    .strategy("MERGE")
                    .definitions(config.getFieldDefinitions().stream()
                            .map(f -> BulkImportFieldDefsRequest.FieldDefItem.builder()
                                    .profileId(profileId)
                                    .profileName(profileName)
                                    .mti(mti)
                                    .deNumber(f.getDeNumber())
                                    .fieldName(f.getFieldName())
                                    .dataType(normalizeDataType(f.getDataType()))
                                    .maxLength(f.getMaxLength())
                                    .isLlvar(Boolean.TRUE.equals(f.getIsLlvar()))
                                    .isLllvar(Boolean.TRUE.equals(f.getIsLllvar()))
                                    .isMandatory(Boolean.TRUE.equals(f.getIsMandatory()))
                                    .displayOrder(0)
                                    .isBuilderVisible(true)
                                    .isActive(true)
                                    .build())
                            .toList())
                    .build();
//            logOutgoingPayload("bulkImportFieldDefinitions", fieldDefsRequest);
            BulkImportResultDto result = rulesServiceClient.bulkImportFieldDefinitions(fieldDefsRequest);
            fieldDefsImported = result.getImported() + result.getUpdated();
        }

        // 4) Bulk import rules
        int rulesImported = 0;
        if (config.getRules() != null && !config.getRules().isEmpty()) {
            BulkImportRulesRequest rulesRequest = BulkImportRulesRequest.builder()
                    .profileId(profileId)
                    .profileName(profileName)
                    .mti(mti)
                    .strategy("MERGE")
                    .rules(config.getRules().stream()
                            .map(r -> BulkImportRulesRequest.RuleItem.builder()
                                    .profileId(profileId)
                                    .profileName(profileName)
                                    .mti(mti)
                                    .deNumber(r.getDeNumber())
                                    .fieldName(r.getFieldName())
                                    .isMandatory(Boolean.TRUE.equals(r.getIsMandatory()))
                                    .minLength(r.getMinLength())
                                    .maxLength(r.getMaxLength())
                                    .dataType(normalizeDataType(r.getDataType()))
                                    .severity(normalizeSeverity(r.getSeverity()))
                                    .priority(1)
                                    .isActive(true)
                                    .build())
                            .toList())
                    .build();
//            logOutgoingPayload("bulkImportRules", rulesRequest);
            BulkImportResultDto result = rulesServiceClient.bulkImportRules(rulesRequest);
            rulesImported = result.getImported() + result.getUpdated();
        }

        // 5) Re-point this document's embedding chunks at the new profile
        List<BrdEmbeddingChunk> chunks = brdEmbeddingChunkRepository.findByBrdDocumentId(brdDocumentId);
        for (BrdEmbeddingChunk chunk : chunks) {
            chunk.setProfileId(profileId);
            brdEmbeddingChunkRepository.save(chunk);
        }

        // 6) Mark document CONFIRMED
        document.setStatus(BrdExtractStatus.CONFIRMED);
        document.setConfirmedBy(confirmedBy);
        document.setConfirmedAt(LocalDateTime.now());
        BrdDocument saved = brdDocumentRepository.save(document);

        log.info("[BRD] Confirmed documentId={} → profileId={} fieldDefs={} rules={}",
                brdDocumentId, profileId, fieldDefsImported, rulesImported);

        return ConfirmResult.builder()
                .brdDocument(saved)
                .profileId(profileId)
                .profileName(profileName)
                .fieldDefinitionsImported(fieldDefsImported)
                .rulesImported(rulesImported)
                .build();
    }

    private BrdExtractedConfig deserialize(String extractedJson) {
        try {
            return objectMapper.readValue(extractedJson, BrdExtractedConfig.class);
        } catch (Exception e) {
            throw new IllegalStateException("Stored extractedJson is corrupt and cannot be confirmed: " + e.getMessage());
        }
    }

    private String normalizeMti(String mti) {
        if (mti == null || mti.isBlank()) return "0200";
        String digitsOnly = mti.replaceAll("\\D", "");
        if (digitsOnly.length() != 4) return "0200";
        return digitsOnly;
    }

    /**
     * Generates a minimal valid jPOS GenericPackager XML from the extracted
     * field definitions. isLlvar → IFA_LLCHAR, isLllvar → IFA_LLLCHAR,
     * else IFA_NUMERIC for numeric dataType and IFA_CHAR for alpha/alphanumeric.
     */
    private String buildPackagerXml(BrdExtractedConfig config) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<!DOCTYPE isopackager PUBLIC \"-//jPOS//DTD GenericPackager 1.0//EN\" \"http://jpos.org/dtd/generic-packager-1.0.dtd\">\n");
        xml.append("<isopackager>\n");
        xml.append("  <isofield id=\"0\" length=\"4\" name=\"MESSAGE TYPE INDICATOR\" class=\"org.jpos.iso.IFA_NUMERIC\"/>\n");
        xml.append("  <isofield id=\"1\" length=\"8\" name=\"BIT MAP\" class=\"org.jpos.iso.IFA_BITMAP\"/>\n");

        List<BrdExtractedConfig.BrdFieldDefinitionDto> defs =
                config.getFieldDefinitions() != null ? config.getFieldDefinitions() : new ArrayList<>();

        for (BrdExtractedConfig.BrdFieldDefinitionDto field : defs) {
            String id = extractDeId(field.getDeNumber());
            if (id == null) continue;

            String isoClass = resolveIsoClass(field);
            String length = field.getMaxLength() != null ? String.valueOf(field.getMaxLength()) : "1";
            String name = field.getFieldName() != null ? escapeXml(field.getFieldName()) : ("DE" + id);

            xml.append("  <isofield id=\"").append(id).append("\" length=\"").append(length)
                    .append("\" name=\"").append(name).append("\" class=\"").append(isoClass).append("\"/>\n");
        }

        xml.append("</isopackager>\n");
        return xml.toString();
    }

    private String extractDeId(String deNumber) {
        if (deNumber == null) return null;
        String digits = deNumber.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }

    private String resolveIsoClass(BrdExtractedConfig.BrdFieldDefinitionDto field) {
        if (Boolean.TRUE.equals(field.getIsLllvar())) return "org.jpos.iso.IFA_LLLCHAR";
        if (Boolean.TRUE.equals(field.getIsLlvar())) return "org.jpos.iso.IFA_LLCHAR";
        String dataType = field.getDataType() != null ? field.getDataType().toLowerCase() : "";
        if (dataType.equals("numeric")) return "org.jpos.iso.IFA_NUMERIC";
        return "org.jpos.iso.IFA_CHAR"; // alpha / alphanumeric / binary / special fallback
    }

    private String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private String normalizeSeverity(String raw){
        if(raw==null||raw.isBlank()) return "WARNING";
        String res=raw.trim().toUpperCase();
        return switch (res){
            case "INFO","ERROR","ADVISORY","LOW","NOTICE"-> "INFO";
            default -> "WARNING";
        };
    }

    private String normalizeDataType(String raw) {
        if (raw == null || raw.isBlank()) return "alphanumeric";
        String s = raw.trim().toLowerCase();
        return switch (s) {
            case "numeric", "alpha", "alphanumeric", "binary", "special" -> s;
            case "n", "num" -> "numeric";
            case "a", "an" -> "alphanumeric";
            default -> "alphanumeric";
        };
    }


    @Data
    @Builder
    @AllArgsConstructor
    public static class ConfirmResult {
        private BrdDocument brdDocument;
        private Long profileId;
        private String profileName;
        private int fieldDefinitionsImported;
        private int rulesImported;
    }
}