
package com.verinite.profile.controller;

import com.verinite.common.dto.ApiResponse;
import com.verinite.profile.dto.*;
import com.verinite.profile.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/profiles")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProfileDto>> createProfile(
            @RequestBody @Valid CreateProfileRequest req,
            @RequestHeader(value = "X-Auth-Username", defaultValue = "system") String username) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(profileService.create(req, username), "Profile created"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProfileDto>>> getAllProfiles(
            @RequestParam(required = false) String env,
            @RequestParam(required = false) Boolean isActive) {
        Boolean effectiveIsActive = isAdmin() ? isActive : Boolean.TRUE;
        return ResponseEntity.ok(ApiResponse.success(
                profileService.getAll(env, effectiveIsActive), "Profiles fetched"));
    }

    private boolean isAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProfileDto>> getProfile(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(profileService.getById(id), "Profile found"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProfileDto>> updateProfile(
            @PathVariable Long id,
            @RequestBody @Valid UpdateProfileRequest req,
            @RequestHeader(value = "X-Auth-Username", defaultValue = "system") String username) {
        return ResponseEntity.ok(ApiResponse.success(profileService.update(id, req, username), "Profile updated"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProfile(@PathVariable Long id,@RequestParam String username) {
        profileService.delete(id,username);
        return ResponseEntity.ok(ApiResponse.success(null, "Profile deleted"));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> setStatus(
            @PathVariable Long id, @RequestParam boolean active,@RequestParam String username) {
        profileService.setActive(id, active,username);
        return ResponseEntity.ok(ApiResponse.success(null,
                active ? "Profile activated" : "Profile deactivated"));
    }


    @PatchMapping("/{id}/default")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> setDefault(@PathVariable Long id,@RequestParam String username) {
        profileService.setDefault(id,username);
        return ResponseEntity.ok(ApiResponse.success(null, "Default profile set"));
    }


    @PostMapping("/{id}/test-connection")
    public ResponseEntity<ApiResponse<TestConnectionResponse>> testConnection(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                profileService.testConnection(id), "Connection tested"));
    }
    @PostMapping("/{id}/clone")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProfileDto>> cloneProfile(
            @PathVariable Long id,
            @RequestParam String newName,
            @RequestHeader(value = "X-Auth-Username", defaultValue = "system") String username) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(profileService.clone(id, newName, username), "Profile cloned"));
    }
}