package com.verinite.profile.controller;

import com.verinite.profile.dto.ProfileFormatResponse;
import com.verinite.profile.service.FormatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/profiles")
@RequiredArgsConstructor
public class InternalProfileController {

    private final FormatService formatService;

    @GetMapping("/{profileId}/format")
    public ResponseEntity<ProfileFormatResponse> getFormatForEngine(
            @PathVariable Long profileId) {
        return ResponseEntity.ok(formatService.getActiveFormatByProfile(profileId));
    }
}