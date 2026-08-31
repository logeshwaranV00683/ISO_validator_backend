package com.verinite.validation.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;


@Configuration
public class FeignAuthHeaderConfig {

    private static final String[] FORWARDED_HEADERS = {
            "X-Auth-User-Id", "X-Auth-Username", "X-Auth-Role", "X-Correlation-ID"
    };

    @Bean
    public RequestInterceptor authHeaderForwardingInterceptor() {
        return (RequestTemplate template) -> {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return; // no inbound request in context (e.g. async/scheduled call)

            HttpServletRequest request = attrs.getRequest();
            for (String header : FORWARDED_HEADERS) {
                String value = request.getHeader(header);
                if (value != null && !value.isBlank()) {
                    template.header(header, value);
                }
            }
        };
    }
}