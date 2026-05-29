package com.verinite.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Injects X-Correlation-ID on every request passing through the gateway.
 *
 * Rules:
 *  - If the incoming request already has an X-Correlation-ID header (e.g. from an
 *    upstream system or a retry), we reuse it — this preserves the trace chain.
 *  - If it's absent, we generate a new UUID.
 *  - The same ID is added to both the downstream REQUEST headers (so every
 *    microservice receives it) and the RESPONSE headers (so the caller can correlate).
 *
 * Ordering: HIGHEST_PRECEDENCE — this must run before JwtAuthFilter (Day 2)
 * so that even rejected requests get a correlation ID in the 401 response.
 */
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = exchange.getRequest()
                .getHeaders()
                .getFirst(CORRELATION_ID_HEADER);

        // Reuse existing ID if present, otherwise mint a new one
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
            log.debug("Generated new Correlation-ID: {}", correlationId);
        } else {
            log.debug("Reusing existing Correlation-ID: {}", correlationId);
        }

        final String finalCorrelationId = correlationId;

        // Mutate the request to inject the header for downstream services
        ServerHttpRequest mutatedRequest = exchange.getRequest()
                .mutate()
                .header(CORRELATION_ID_HEADER, finalCorrelationId)
                .build();

        // Also add to the response so the caller can trace the request
        exchange.getResponse()
                .getHeaders()
                .add(CORRELATION_ID_HEADER, finalCorrelationId);

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        // Must be highest — runs before every other filter including JwtAuthFilter
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
