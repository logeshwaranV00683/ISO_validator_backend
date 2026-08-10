package com.verinite.profile.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Stamps X-Auth-* headers onto Feign calls to rules-service so its
 * @PreAuthorize("hasRole('ADMIN')") checks pass. These headers are normally
 * injected by the API Gateway on inbound requests; Feign doesn't forward
 * them automatically, so we re-derive them from the caller's already
 * authenticated SecurityContext (format create/update already requires
 * ADMIN to reach this code) and fall back to "system" if none is present.
 */
@Configuration
public class InternalFeignConfig {

    @Bean
    public RequestInterceptor internalAuthHeaderInterceptor() {
        return template -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = (auth != null && auth.getName() != null) ? auth.getName() : "system";
            template.header("X-Auth-Username", username);
            template.header("X-Auth-Role", "ADMIN");
            template.header("X-Auth-User-Id", username);
        };
    }
}