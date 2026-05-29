package com.verinite.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Global CORS configuration for the API Gateway.
 *
 * This handles CORS at the gateway level so individual downstream services
 * don't need their own CORS config.
 *
 * NOTE: allowedOriginPatterns("*") with allowCredentials(true) is fine for
 * dev/internal tools. For production, replace "*" with the actual React app
 * origin (e.g. "http://internal-tool.verinite.com").
 *
 * The DedupeResponseHeader filter in application.yml removes duplicate
 * Access-Control-Allow-* headers that can appear when both the gateway
 * and a downstream service set them.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // Allow any origin pattern — tighten to specific origin in production
        config.setAllowedOriginPatterns(List.of("*"));

        // All standard HTTP methods including OPTIONS (preflight)
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Allow all headers — including Authorization, Content-Type, X-Correlation-ID
        config.setAllowedHeaders(List.of("*"));

        // Expose correlation ID header to the browser/client
        config.setExposedHeaders(List.of("X-Correlation-ID"));

        // Required for JWT in Authorization header with credentials
        config.setAllowCredentials(true);

        // Cache preflight response for 1 hour (reduces OPTIONS round-trips)
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}
