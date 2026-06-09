package com.verinite.profile.service;

import com.verinite.profile.dto.CreateFormatRequest;
import com.verinite.profile.dto.FormatDto;
import com.verinite.profile.dto.ProfileFormatResponse;
import com.verinite.profile.entity.MessageFormat;
import com.verinite.profile.entity.MessageFormatVersion;
import com.verinite.profile.entity.SwitchProfile;
import com.verinite.profile.event.FormatEventPublisher;
import com.verinite.profile.repository.MessageFormatRepository;
import com.verinite.profile.repository.MessageFormatVersionRepository;
import com.verinite.profile.repository.SwitchProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FormatService {

    private static final Pattern DOCTYPE_PATTERN =
            Pattern.compile("(?s)<!DOCTYPE[^\\[>]*(?:\\[[^\\]]*])?\\s*>\\s*");

    private final MessageFormatRepository        formatRepo;
    private final MessageFormatVersionRepository formatVersionRepo;
    private final SwitchProfileRepository        profileRepo;
    private final FormatEventPublisher           formatEventPublisher;

    public FormatDto create(CreateFormatRequest req, String username) {
        MessageFormat format = MessageFormat.builder()
                .profileId(req.getProfileId())
                .formatName(req.getFormatName())
                .mti(req.getMti())
                .xmlContent(req.getXmlContent())
                .createdBy(username)
                .build();
        MessageFormat saved = formatRepo.save(format);
        log.info("Created format id={} profileId={}", saved.getId(), saved.getProfileId());
        return mapToDto(saved);
    }

    public FormatDto getById(Long id) {
        MessageFormat format = formatRepo.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Format not found: " + id));
        return mapToDto(format);
    }

    public FormatDto update(Long id, String newXmlContent, String username) {
        MessageFormat format = formatRepo.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Format not found: " + id));

        formatVersionRepo.save(MessageFormatVersion.builder()
                .formatId(id)
                .versionNumber(format.getCurrentVersion())
                .xmlContent(format.getXmlContent())
                .createdBy(username)
                .build());

        format.setXmlContent(newXmlContent);
        format.setCurrentVersion(format.getCurrentVersion() + 1);
        formatRepo.save(format);

        formatEventPublisher.publishFormatUpdated(format.getProfileId(), id);
        log.info("Updated format id={} to version={}", id, format.getCurrentVersion());
        return mapToDto(format);
    }

    /** PUT /formats/{id}/rollback — rolls back to (currentVersion - 1), no version param */
    public FormatDto rollback(Long id) {
        return rollbackToVersion(id, null);
    }

    /** PUT /formats/{id}/rollback/{version} — rolls back to a specific version (F7) */
    public FormatDto rollbackToVersion(Long id, Integer targetVersionNum) {
        MessageFormat format = formatRepo.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Format not found: " + id));

        int prevVersionNum = (targetVersionNum != null)
                ? targetVersionNum
                : format.getCurrentVersion() - 1;

        if (prevVersionNum < 1) {
            throw new RuntimeException("No previous version to rollback to");
        }

        MessageFormatVersion prevVersion = formatVersionRepo
                .findByFormatIdAndVersionNumber(id, prevVersionNum)
                .orElseThrow(() -> new RuntimeException("Version " + prevVersionNum + " not found"));

        format.setXmlContent(prevVersion.getXmlContent());
        format.setCurrentVersion(format.getCurrentVersion() + 1);
        formatRepo.save(format);

        formatEventPublisher.publishFormatRolledBack(format.getProfileId(), id);
        log.info("Rolled back format id={} to content of v{} (new version={})",
                id, prevVersionNum, format.getCurrentVersion());
        return mapToDto(format);
    }

    public List<FormatDto> getVersions(Long id) {
        return formatVersionRepo.findAllByFormatIdOrderByVersionNumberDesc(id)
                .stream()
                .map(v -> FormatDto.builder()
                        .id(v.getId())
                        .xmlContent(v.getXmlContent())
                        .currentVersion(v.getVersionNumber())
                        .createdAt(v.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    /** GET /internal/profiles/{profileId}/format — now includes profileName */
    public ProfileFormatResponse getActiveFormatByProfile(Long profileId) {
        MessageFormat format = formatRepo.findByProfileIdAndDeletedAtIsNull(profileId)
                .orElseThrow(() -> new RuntimeException("No format found for profile: " + profileId));

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

    public List<FormatDto> getAll() {
        return formatRepo.findAllByDeletedAtIsNull()
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public FormatDto mapToDto(MessageFormat f) {
        return FormatDto.builder()
                .id(f.getId())
                .profileId(f.getProfileId())
                .formatName(f.getFormatName())
                .mti(f.getMti())
                .xmlContent(f.getXmlContent())
                .currentVersion(f.getCurrentVersion())
                .createdAt(f.getCreatedAt())
                .updatedAt(f.getUpdatedAt())
                .build();
    }

    public void delete(Long id) {
        MessageFormat format = formatRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Format not found: " + id));
        format.setDeletedAt(java.time.LocalDateTime.now());
        formatRepo.save(format);
        log.info("Soft-deleted format id={}", id);
    }

    public void setActive(Long id, boolean active) {
        MessageFormat format = formatRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Format not found: " + id));
        format.setStatus(MessageFormat.Status.active);
        formatRepo.save(format);
    }

    /** POST /formats/validate-xml — DTD stripped same as PackagerCache so it doesn't fail */
    public void validateXml(String xmlContent) {
        try {
            String cleanXml = DOCTYPE_PATTERN.matcher(xmlContent).replaceAll("").trim();
            new org.jpos.iso.packager.GenericPackager(
                    new ByteArrayInputStream(cleanXml.getBytes(StandardCharsets.UTF_8)));
            log.info("XML validation passed — packager loaded");
        } catch (Exception e) {
            throw new RuntimeException("Invalid jPOS XML: " + e.getMessage(), e);
        }
    }

    public void reload(Long id) {
        MessageFormat format = formatRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Format not found: " + id));
        formatEventPublisher.publishFormatUpdated(null, id);
        log.info("Reload signal published for formatId={}", id);
    }
}