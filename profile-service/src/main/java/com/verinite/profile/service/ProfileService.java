package com.verinite.profile.service;

import com.verinite.profile.dto.*;
import com.verinite.profile.entity.SwitchProfile;
import com.verinite.profile.event.AuditEventPublisher;
import com.verinite.profile.repository.SwitchProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileService {

    private final SwitchProfileRepository profileRepo;
    private final AuditEventPublisher      auditPublisher;

    public ProfileDto create(CreateProfileRequest req, String username) {
        if (profileRepo.existsByProfileName(req.getProfileName())) {
            throw new RuntimeException("Profile name already exists: " + req.getProfileName());
        }
        SwitchProfile profile = SwitchProfile.builder()
                .profileName(req.getProfileName())
                .description(req.getDescription())
                .createdBy(username)
                .build();
        SwitchProfile saved = profileRepo.save(profile);
        auditPublisher.publish("CREATE", "PROFILE", saved.getId(), saved.getProfileName(),
                username, null, null, null, "Profile created", null);
        log.info("Created profile id={} name={}", saved.getId(), saved.getProfileName());
        return mapToDto(saved);
    }

    public List<ProfileDto> getAll(String env, Boolean isActive) {
        return profileRepo.findAllByDeletedAtIsNull().stream()
                .filter(p -> env == null || env.equalsIgnoreCase(
                        p.getEnvironment() != null ? p.getEnvironment().name() : ""))
                .filter(p -> isActive == null || isActive.equals(p.getActive()))
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public ProfileDto getById(Long id) {
        return mapToDto(findOrThrow(id));
    }

    public ProfileDto update(Long id, UpdateProfileRequest req, String username) {
        SwitchProfile profile = findOrThrow(id);
        String before = profile.getProfileName();
        if (req.getProfileName() != null) profile.setProfileName(req.getProfileName());
        if (req.getDescription()  != null) profile.setDescription(req.getDescription());
        profile.setUpdatedBy(username);
        SwitchProfile saved = profileRepo.save(profile);
        auditPublisher.publish("UPDATE", "PROFILE", id, saved.getProfileName(),
                username, null, before, req.getProfileName(), "Profile updated", null);
        return mapToDto(saved);
    }

    public void setActive(Long id, boolean active) {
        SwitchProfile profile = findOrThrow(id);
        profile.setActive(active);
        profileRepo.save(profile);
        log.info("Profile id={} active={}", id, active);
    }

    @Transactional
    public void setDefault(Long id) {
        // Clear any existing default first
        profileRepo.findAllByDeletedAtIsNull().stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsDefault()))
                .forEach(p -> { p.setIsDefault(false); profileRepo.save(p); });
        SwitchProfile profile = findOrThrow(id);
        profile.setIsDefault(true);
        profileRepo.save(profile);
        log.info("Profile id={} set as default", id);
    }

    public TestConnectionResponse testConnection(Long id) {
        SwitchProfile profile = findOrThrow(id);
        String host    = profile.getHost();
        int    port    = profile.getPort() != null ? profile.getPort() : 0;
        int    timeout = profile.getConnectionTimeoutMs() != null
                ? profile.getConnectionTimeoutMs() : 30000;

        long start = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeout);
            int latency = (int) (System.currentTimeMillis() - start);
            profile.setLastTestedAt(LocalDateTime.now());
            profile.setLastTestResult(SwitchProfile.TestResult.OK);
            profile.setLastTestLatencyMs(latency);
            profile.setLastTestMessage("Connection established");
            profileRepo.save(profile);
            return TestConnectionResponse.builder()
                    .profileId(id).host(host).port(port)
                    .result("OK").message("Connection established in " + latency + "ms")
                    .latencyMs(latency).testedAt(LocalDateTime.now()).build();
        } catch (Exception e) {
            int latency = (int) (System.currentTimeMillis() - start);
            profile.setLastTestedAt(LocalDateTime.now());
            profile.setLastTestResult(SwitchProfile.TestResult.FAILED);
            profile.setLastTestMessage(e.getMessage());
            profileRepo.save(profile);
            return TestConnectionResponse.builder()
                    .profileId(id).host(host).port(port)
                    .result("FAILED").message(e.getMessage())
                    .latencyMs(latency).testedAt(LocalDateTime.now()).build();
        }
    }

    public ProfileDto clone(Long id, String newName, String username) {
        SwitchProfile original = findOrThrow(id);
        if (profileRepo.existsByProfileName(newName)) {
            throw new RuntimeException("Profile name already exists: " + newName);
        }
        SwitchProfile cloned = SwitchProfile.builder()
                .profileName(newName)
                .description(original.getDescription())
                .environment(original.getEnvironment())
                .host(original.getHost())
                .port(original.getPort())
                .timezone(original.getTimezone())
                .connectionTimeoutMs(original.getConnectionTimeoutMs())
                .tpduEnabled(original.getTpduEnabled())
                .tpduValue(original.getTpduValue())
                .active(false)
                .isDefault(false)
                .createdBy(username)
                .build();
        SwitchProfile saved = profileRepo.save(cloned);
        auditPublisher.publish("CREATE", "PROFILE", saved.getId(), newName,
                username, null, null, null,
                "Cloned from profile id=" + id, null);
        return mapToDto(saved);
    }

    public void delete(Long id) {
        SwitchProfile profile = findOrThrow(id);
        profile.setDeletedAt(LocalDateTime.now());
        profileRepo.save(profile);
    }

    private SwitchProfile findOrThrow(Long id) {
        return profileRepo.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Profile not found: " + id));
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