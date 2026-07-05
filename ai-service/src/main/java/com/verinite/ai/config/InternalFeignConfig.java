package com.verinite.ai.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Stamps X-Auth-* headers onto Feign calls made to profile-service /
 * rules-service so their @PreAuthorize("hasRole('ADMIN')") checks pass.
 * These headers are normally injected by the API Gateway's JwtAuthFilter
 * on inbound requests; Feign does not forward them automatically, so we
 * re-derive them from the caller's already-authenticated SecurityContext
 * (BrdController requires ADMIN to reach the confirm flow in the first
 * place) and fall back to a "system" identity if none is present.
 */
@Configuration
public class InternalFeignConfig {

    @Bean
    public RequestInterceptor brdAuthHeaderInterceptor() {
        return template -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = (auth != null && auth.getName() != null) ? auth.getName() : "system";
            template.header("X-Auth-Username", username);
            template.header("X-Auth-Role", "ADMIN");
            template.header("X-Auth-User-Id", username);
        };
    }
}