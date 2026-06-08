package com.verinite.auth_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads X-Auth-* headers injected by the API Gateway JwtAuthFilter and
 * populates the Spring Security context so @PreAuthorize annotations work.
 *
 * /auth/login and /auth/logout bypass the JwtAuthFilter in the gateway
 * (they are in PUBLIC_PATHS), so no headers are injected for those endpoints —
 * they remain anonymous here, which is correct.
 */
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String userId        = request.getHeader("X-Auth-User-Id");
        String username      = request.getHeader("X-Auth-Username");
        String role          = request.getHeader("X-Auth-Role");
        String correlationId = request.getHeader("X-Correlation-Id");

        if (username != null && role != null) {
            var authority = new SimpleGrantedAuthority("ROLE_" + role.toUpperCase());
            var auth = new UsernamePasswordAuthenticationToken(
                    username, null, List.of(authority));
            auth.setDetails(new UserContextDetails(userId, username, role, correlationId));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(request, response);
    }

    public record UserContextDetails(
            String userId, String username, String role, String correlationId) {}
}