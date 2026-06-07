package com.verinite.ai.config;

import com.verinite.ai.security.HeaderAuthenticationFilter;
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
                .addFilterBefore(new HeaderAuthenticationFilter(),
                        UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/internal/**").permitAll()   // network-isolated
                        .requestMatchers(
                                "/swagger-ui.html", "/swagger-ui/**",
                                "/api-docs/**",     "/v3/api-docs/**"
                        ).permitAll()
                        // @PreAuthorize on methods handles ADMIN enforcement
                        .anyRequest().permitAll()
                );
        return http.build();
    }
}