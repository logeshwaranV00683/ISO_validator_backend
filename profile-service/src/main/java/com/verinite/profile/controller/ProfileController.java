package com.verinite.profile.controller;

import com.verinite.common.dto.ApiResponse;
import com.verinite.profile.dto.CreateProfileRequest;
import com.verinite.profile.dto.ProfileDto;
import com.verinite.profile.dto.UpdateProfileRequest;
import com.verinite.profile.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/profiles")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @PostMapping
    public ResponseEntity<ApiResponse<ProfileDto>> createProfile(
            @RequestBody @Valid CreateProfileRequest req) {
        ProfileDto created = profileService.create(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Profile created"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProfileDto>>> getAllProfiles() {
        return ResponseEntity.ok(ApiResponse.success(profileService.getAll(), "Profiles fetched"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProfileDto>> getProfile(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(profileService.getById(id), "Profile found"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProfileDto>> updateProfile(
            @PathVariable Long id,
            @RequestBody @Valid UpdateProfileRequest req) {
        return ResponseEntity.ok(ApiResponse.success(profileService.update(id, req), "Profile updated"));
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activateProfile(@PathVariable Long id) {
        profileService.setActive(id, true);
        return ResponseEntity.ok(ApiResponse.success(null, "Profile activated"));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivateProfile(@PathVariable Long id) {
        profileService.setActive(id, false);
        return ResponseEntity.ok(ApiResponse.success(null, "Profile deactivated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProfile(@PathVariable Long id) {
        profileService.delete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}