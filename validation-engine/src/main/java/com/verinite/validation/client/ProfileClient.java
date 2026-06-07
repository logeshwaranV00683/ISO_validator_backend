package com.verinite.validation.client;

import com.verinite.validation.dto.ProfileFormatResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "profile-service")
public interface ProfileClient {

    /**
     * Returns the active format for the profile — includes formatId and XML content.
     * Called by ValidationCacheService.getProfileFormat().
     */
    @GetMapping("/internal/profiles/{profileId}/format")
    ProfileFormatResponseDto getFormatForProfile(@PathVariable Long profileId);
}