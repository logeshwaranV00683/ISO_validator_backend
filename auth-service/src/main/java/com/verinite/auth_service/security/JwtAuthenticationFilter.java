package com.verinite.auth_service.security;

import com.verinite.auth_service.repository.UserSessionRepository;
import com.verinite.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final UserSessionRepository userSessionRepository;

    @Value("${jwt.public-key-path}")
    private String publicKeyPath;

    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        try {
            String token = header.substring(7);
            PublicKey publicKey = loadPublicKey();
            Claims claims = JwtUtil.parseToken(token, publicKey);

            if (JwtUtil.isExpired(claims)) {
                chain.doFilter(request, response);
                return;
            }

            // Check session not revoked
            String jti = claims.getId();
            boolean valid = userSessionRepository.findByJti(jti)
                    .map(s -> s.getRevokedAt() == null)
                    .orElse(false);

            if (!valid) {
                chain.doFilter(request, response);
                return;
            }

            // Set Spring Security context
            String role = JwtUtil.extractRole(claims);
            String username = JwtUtil.extractUsername(claims);

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            username,
                            null,
                            List.of(new SimpleGrantedAuthority(
                                    "ROLE_" + role)));

            SecurityContextHolder.getContext().setAuthentication(auth);
            request.setAttribute("jti", jti);

        } catch (Exception e) {
            log.warn("JWT filter error: {}", e.getMessage());
        }

        chain.doFilter(request, response);
    }

    private PublicKey loadPublicKey() throws Exception {
        String content = new String(
                Files.readAllBytes(Paths.get(publicKeyPath)));
        String clean = content
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] keyBytes = Base64.getDecoder().decode(clean);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }
}