package com.verinite.profile.service;

import com.verinite.profile.dto.CreateFormatRequest;
import com.verinite.profile.dto.FormatDto;
import com.verinite.profile.dto.ProfileFormatResponse;
import com.verinite.profile.entity.MessageFormat;
import com.verinite.profile.repository.MessageFormatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class FormatService {

    private final MessageFormatRepository formatRepo;

    public FormatDto create(CreateFormatRequest req) {
        MessageFormat format = MessageFormat.builder()
                .profileId(req.getProfileId())
                .formatName(req.getFormatName())
                .mti(req.getMti())
                .xmlContent(req.getXmlContent())
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
}