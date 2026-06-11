package com.verinite.profile.service;

import com.verinite.profile.dto.*;
import com.verinite.profile.entity.SwitchProfile;
import com.verinite.profile.event.AuditEventPublisher;
import com.verinite.profile.event.AuditEventPublisher.AuditEvent;
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
                .environment(req.getEnvironment())
                .description(req.getDescription())
                .host(req.getHost())
                .port(req.getPort())
                .timezone(req.getTimezone() != null ? req.getTimezone() : "UTC")
                .connectionTimeoutMs(req.getConnectionTimeoutMs() != null
                        ? req.getConnectionTimeoutMs() : 30000)
                .tpduEnabled(req.isTpduEnabled())
                .tpduValue(req.getTpduValue())
                .active(req.isActive())
                .isDefault(req.isDefault())
                .createdBy(username)
                .updatedBy(username)
                .build();

        SwitchProfile saved = profileRepo.save(profile);

        auditPublisher.publish(AuditEvent.builder()
                .action("CREATE")
                .entityType("PROFILE")
                .entityId(saved.getId())
                .entityName(saved.getProfileName())
                .username(username)
                .description("Profile created")
                .build());

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

    @Transactional
    public ProfileDto update(Long id, UpdateProfileRequest req, String username) {
        SwitchProfile profile = findOrThrow(id);

        String before = profile.getProfileName();

        if (req.getProfileName()        != null) profile.setProfileName(req.getProfileName());
        if (req.getDescription()        != null) profile.setDescription(req.getDescription());
        if (req.getEnvironment()        != null) profile.setEnvironment(req.getEnvironment());  // FIX: was missing
        if (req.getHost()               != null) profile.setHost(req.getHost());
        if (req.getPort()               != null) profile.setPort(req.getPort());
        if (req.getTimezone()           != null) profile.setTimezone(req.getTimezone());
        if (req.getConnectionTimeoutMs() != null) profile.setConnectionTimeoutMs(req.getConnectionTimeoutMs());
        if (req.getTpduEnabled()        != null) profile.setTpduEnabled(req.getTpduEnabled());
        if (req.getTpduValue()          != null) profile.setTpduValue(req.getTpduValue());
        if (req.getIsActive()           != null) profile.setActive(req.getIsActive());
        profile.setUpdatedBy(username);

        SwitchProfile saved = profileRepo.save(profile);

        auditPublisher.publish(AuditEvent.builder()
                .action("UPDATE")
                .entityType("PROFILE")
                .entityId(id)
                .entityName(saved.getProfileName())
                .username(username)
                .beforeValue(before)
                .afterValue(saved.getProfileName())
                .description("Profile updated")
                .build());

        log.info("Updated profile id={} name={}", saved.getId(), saved.getProfileName());
        return mapToDto(saved);
    }

    public void setActive(Long id, boolean active, String username) {
        SwitchProfile profile = findOrThrow(id);
        profile.setActive(active);
        profile.setUpdatedBy(username);
        profileRepo.save(profile);
        auditPublisher.publish(AuditEvent.builder()
                .action("SET_ACTIVE")
                .entityType("PROFILE")
                .entityId(id)
                .entityName(profile.getProfileName())
                .username(username)
                .description("Profile active set to " + active)
                .build());
        log.info("Profile id={} active={}", id, active);
    }

    @Transactional
    public void setDefault(Long id, String username) {
        // Clear any existing default first (service-level enforcement)
        profileRepo.findAllByDeletedAtIsNull().stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsDefault()))
                .forEach(p -> { p.setIsDefault(false); profileRepo.save(p); });

        SwitchProfile profile = findOrThrow(id);
        profile.setIsDefault(true);
        profile.setUpdatedBy(username);
        profileRepo.save(profile);

        auditPublisher.publish(AuditEvent.builder()
                .action("SET_DEFAULT")
                .entityType("PROFILE")
                .entityId(id)
                .entityName(profile.getProfileName())
                .username(username)
                .description("Profile set as default")
                .build());

        log.info("Profile id={} set as default", id);
    }

    public TestConnectionResponse testConnection(Long id) {
        SwitchProfile profile = findOrThrow(id);
        String host    = profile.getHost();
        int    port    = profile.getPort() != null    ? profile.getPort()               : 0;
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
                .updatedBy(username)
                .build();
        SwitchProfile saved = profileRepo.save(cloned);
        auditPublisher.publish(AuditEvent.builder()
                .action("CLONE")
                .entityType("PROFILE")
                .entityId(saved.getId())
                .entityName(newName)
                .username(username)
                .description("Cloned from profile id=" + id)
                .build());
        log.info("Cloned profile id={} → new id={} name={}", id, saved.getId(), newName);
        return mapToDto(saved);
    }

    public void delete(Long id, String username) {
        SwitchProfile profile = findOrThrow(id);
        profile.setDeletedAt(LocalDateTime.now());
        profile.setUpdatedBy(username);
        profileRepo.save(profile);
        auditPublisher.publish(AuditEvent.builder()
                .action("DELETE")
                .entityType("PROFILE")
                .entityId(id)
                .entityName(profile.getProfileName())
                .username(username)
                .description("Profile soft-deleted")
                .build());
        log.info("Soft-deleted profile id={}", id);
    }

    // ── private ──────────────────────────────────────────────────────────

    private SwitchProfile findOrThrow(Long id) {
        return profileRepo.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Profile not found: " + id));
    }

    private ProfileDto mapToDto(SwitchProfile p) {
        return ProfileDto.builder()
                .id(p.getId())
                .profileName(p.getProfileName())
                .description(p.getDescription())
                .environment(p.getEnvironment())
                .host(p.getHost())
                .port(p.getPort())
                .timezone(p.getTimezone())
                .connectionTimeoutMs(p.getConnectionTimeoutMs())
                .tpduEnabled(p.getTpduEnabled())
                .tpduValue(p.getTpduValue())
                .active(p.getActive())
                .isDefault(p.getIsDefault())
                .lastUsedAt(p.getLastUsedAt())          // FIX: was missing
                .lastTestedAt(p.getLastTestedAt())
                .lastTestResult(p.getLastTestResult())
                .lastTestLatencyMs(p.getLastTestLatencyMs())
                .lastTestMessage(p.getLastTestMessage())
                .createdBy(p.getCreatedBy())
                .updatedBy(p.getUpdatedBy())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}