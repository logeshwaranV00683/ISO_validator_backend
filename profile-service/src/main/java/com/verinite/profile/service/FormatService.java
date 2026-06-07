package com.verinite.profile.service;

import com.verinite.profile.dto.CreateFormatRequest;
import com.verinite.profile.dto.FormatDto;
import com.verinite.profile.dto.ProfileFormatResponse;
import com.verinite.profile.entity.MessageFormat;
import com.verinite.profile.entity.MessageFormatVersion;
import com.verinite.profile.event.FormatEventPublisher;
import com.verinite.profile.repository.MessageFormatRepository;
import com.verinite.profile.repository.MessageFormatVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FormatService {

    private final MessageFormatRepository formatRepo;
    private final MessageFormatVersionRepository formatVersionRepo;
    private final FormatEventPublisher formatEventPublisher;

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

    // PUT /formats/{id} — save current to versions, bump version
    public FormatDto update(Long id, String newXmlContent, String username) {
        MessageFormat format = formatRepo.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Format not found: " + id));

        // Save current content as a version before overwriting
        MessageFormatVersion versionRecord = MessageFormatVersion.builder()
                .formatId(id)
                .versionNumber(format.getCurrentVersion())
                .xmlContent(format.getXmlContent())
                .createdBy(username)
                .build();
        formatVersionRepo.save(versionRecord);

        // Update with new content and bump version
        format.setXmlContent(newXmlContent);
        format.setCurrentVersion(format.getCurrentVersion() + 1);
        formatRepo.save(format);

        formatEventPublisher.publishFormatUpdated(format.getProfileId(), id);
        log.info("Updated format id={} to version={}", id, format.getCurrentVersion());
        return mapToDto(format);
    }

    // PUT /formats/{id}/rollback — restore previous version
    public FormatDto rollback(Long id) {
        MessageFormat format = formatRepo.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Format not found: " + id));

        int prevVersionNum = format.getCurrentVersion() - 1;
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
        log.info("Rolled back format id={} to content of v{}", id, prevVersionNum);
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

    // /internal/profiles/{profileId}/format
    public ProfileFormatResponse getActiveFormatByProfile(Long profileId) {
        MessageFormat format = formatRepo.findByProfileIdAndDeletedAtIsNull(profileId)
                .orElseThrow(() -> new RuntimeException("No format found for profile: " + profileId));
        return new ProfileFormatResponse(
                format.getId(),
                format.getXmlContent(),
                format.getMti(),
                format.getProfileId()
        );
    }

    // Add to FormatService.java
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

    public void validateXml(String xmlContent) {
        try {
            org.jpos.iso.packager.GenericPackager packager =
                    new org.jpos.iso.packager.GenericPackager(
                            new java.io.ByteArrayInputStream(
                                    xmlContent.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            log.info("XML validation passed — packager loaded");
        } catch (Exception e) {
            throw new RuntimeException("Invalid jPOS XML: " + e.getMessage(), e);
        }
    }

    public void reload(Long id) {
        MessageFormat format = formatRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Format not found: " + id));
        // Publish FORMAT_UPDATED — validation-engine will evict packager cache and reload
        formatEventPublisher.publishFormatUpdated(null, id);
        log.info("Reload signal published for formatId={}", id);
    }
}