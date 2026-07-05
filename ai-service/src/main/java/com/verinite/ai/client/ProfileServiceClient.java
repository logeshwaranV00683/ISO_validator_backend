package com.verinite.ai.client;

import com.verinite.ai.config.InternalFeignConfig;
import com.verinite.ai.dto.CreateFormatRequest;
import com.verinite.ai.dto.CreateProfileRequest;
import com.verinite.ai.dto.ProfileDto;
import com.verinite.ai.dto.FormatDto;
import com.verinite.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Calls profile-service's ADMIN-protected public endpoints (not /internal/**)
 * to create a switch profile + format from a confirmed BRD extraction.
 * Requires InternalFeignConfig to stamp X-Auth-* headers so @PreAuthorize('ADMIN')
 * on the profile-service side passes.
 */
@FeignClient(name = "profile-service", configuration = InternalFeignConfig.class)
public interface ProfileServiceClient {

    @PostMapping("/profiles")
    ApiResponse<ProfileDto> createProfile(@RequestBody CreateProfileRequest request);

    @PostMapping("/formats")
    ApiResponse<FormatDto> createFormat(@RequestBody CreateFormatRequest request);
}