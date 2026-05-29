package com.verinite.profile.service;

import com.verinite.profile.dto.CreateProfileRequest;
import com.verinite.profile.dto.ProfileDto;
import com.verinite.profile.dto.UpdateProfileRequest;
import com.verinite.profile.entity.SwitchProfile;
import com.verinite.profile.repository.SwitchProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileService {

    private final SwitchProfileRepository profileRepo;

    public ProfileDto create(CreateProfileRequest req) {
        if (profileRepo.existsByProfileName(req.getProfileName())) {
            throw new RuntimeException("Profile name already exists: " + req.getProfileName());
        }
        SwitchProfile profile = SwitchProfile.builder()
                .profileName(req.getProfileName())
                .description(req.getDescription())
                .build();
        SwitchProfile saved = profileRepo.save(profile);
        log.info("Created profile id={} name={}", saved.getId(), saved.getProfileName());
        return mapToDto(saved);
    }

    public List<ProfileDto> getAll() {
        return profileRepo.findAllByDeletedFalse()
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public ProfileDto getById(Long id) {
        SwitchProfile profile = profileRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new RuntimeException("Profile not found: " + id));
        return mapToDto(profile);
    }

    public ProfileDto update(Long id, UpdateProfileRequest req) {
        SwitchProfile profile = profileRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new RuntimeException("Profile not found: " + id));
        if (req.getProfileName() != null) profile.setProfileName(req.getProfileName());
        if (req.getDescription() != null) profile.setDescription(req.getDescription());
        return mapToDto(profileRepo.save(profile));
    }

    public void setActive(Long id, boolean active) {
        SwitchProfile profile = profileRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new RuntimeException("Profile not found: " + id));
        profile.setActive(active);
        profileRepo.save(profile);
        log.info("Profile id={} active={}", id, active);
    }

    public void delete(Long id) {
        SwitchProfile profile = profileRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new RuntimeException("Profile not found: " + id));
        profile.setDeleted(true);
        profileRepo.save(profile);
    }

    private ProfileDto mapToDto(SwitchProfile p) {
        return ProfileDto.builder()
                .id(p.getId())
                .profileName(p.getProfileName())
                .description(p.getDescription())
                .active(p.getActive())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}