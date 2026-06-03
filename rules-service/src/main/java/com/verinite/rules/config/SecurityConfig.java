package com.verinite.rules.config;

import com.verinite.rules.security.HeaderAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity   // enables @PreAuthorize on controller methods
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                // Reads X-Auth-* headers from gateway → populates SecurityContext
                .addFilterBefore(new HeaderAuthenticationFilter(),
                        UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/internal/**").permitAll()   // network-isolated; no JWT needed
                        .requestMatchers(
                                "/swagger-ui.html", "/swagger-ui/**",
                                "/api-docs/**", "/v3/api-docs/**"
                        ).permitAll()
                        // @PreAuthorize on individual methods handles ADMIN enforcement
                        .anyRequest().permitAll()
                );
        return http.build();
    }
}