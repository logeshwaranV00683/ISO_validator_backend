package com.verinite.gateway.filter;

import com.fasterxml.jackson.databind.JsonNode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.PublicKey;
import java.util.List;

@Slf4j
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private final PublicKey publicKey;
    private final WebClient webClient;

    public JwtAuthFilter(PublicKey publicKey, WebClient.Builder loadBalancedWebClientBuilder) {
        this.publicKey = publicKey;
        this.webClient = loadBalancedWebClientBuilder.baseUrl("lb://auth-service").build();
    }

    private static final List<String> PUBLIC_PATHS = List.of(
            "/auth/login",
            "/auth/logout",
            "/actuator"
    );

    @Override
    public int getOrder() { return -1; }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange,
                             GatewayFilterChain chain) {

        String path = exchange.getRequest().getPath().toString();

        if (PUBLIC_PATHS.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest()
                .getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return rejectWith401(exchange, "Missing Authorization header");
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String jti = claims.getId();

            // BUG 4 FIX: signature/expiry alone isn't enough — a revoked session's
            // JWT still has a valid signature until it naturally expires. Ask
            // auth-service whether the session behind this jti is still live.
            return checkSessionActive(jti)
                    .flatMap(active -> {
                        if (!active) {
                            return rejectWith401(exchange, "Session revoked");
                        }

                        ServerHttpRequest mutatedRequest = exchange.getRequest()
                                .mutate()
                                .header("X-Auth-User-Id",  claims.getSubject())
                                .header("X-Auth-Username", (String) claims.get("username"))
                                .header("X-Auth-Role",     (String) claims.get("role"))
                                .build();

                        return chain.filter(exchange.mutate().request(mutatedRequest).build());
                    });

        } catch (ExpiredJwtException e) {
            return rejectWith401(exchange, "Token expired");
        } catch (JwtException e) {
            return rejectWith401(exchange, "Invalid token");
        }
    }

    private Mono<Boolean> checkSessionActive(String jti) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/auth/validate-token")
                        .queryParam("jti", jti)
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(body -> body.path("data").asBoolean(false))
                // Fail-closed: if auth-service is unreachable or errors, treat the
                // session as NOT valid rather than letting a possibly-revoked
                // token through. Reject on the safe side, not the available side.
                .onErrorResume(ex -> {
                    log.warn("Session validation call failed for jti={}: {}", jti, ex.toString());
                    return Mono.just(false);
                });
    }

    private Mono<Void> rejectWith401(ServerWebExchange exchange, String message) {

        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] body = ("{\"success\":false,\"message\":\"" + message + "\"}").getBytes();
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);

        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}