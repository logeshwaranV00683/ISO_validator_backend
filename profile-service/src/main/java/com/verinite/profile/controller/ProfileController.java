package com.verinite.profile.controller;

import com.verinite.profile.dto.CreateProfileRequest;
import com.verinite.profile.dto.ProfileDto;
import com.verinite.profile.dto.UpdateProfileRequest;
import com.verinite.profile.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/profiles")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @PostMapping("/create")
    public ResponseEntity<ProfileDto> createProfile(
            @RequestBody @Valid CreateProfileRequest req) {
        return ResponseEntity.status(201).body(profileService.create(req));
    }

    @GetMapping("/getAll")
    public ResponseEntity<List<ProfileDto>> getAllProfiles() {
        return ResponseEntity.ok(profileService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProfileDto> getProfile(@PathVariable Long id) {
        return ResponseEntity.ok(profileService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProfileDto> updateProfile(
            @PathVariable Long id,
            @RequestBody UpdateProfileRequest req) {
        return ResponseEntity.ok(profileService.update(id, req));
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activateProfile(@PathVariable Long id) {
        profileService.setActive(id, true);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateProfile(@PathVariable Long id) {
        profileService.setActive(id, false);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProfile(@PathVariable Long id) {
        profileService.delete(id);
        return ResponseEntity.noContent().build();
    }
}