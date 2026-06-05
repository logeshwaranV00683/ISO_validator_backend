package com.verinite.rules.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Static helper — extracts user info from Spring Security context.
 * Populated by HeaderAuthenticationFilter from X-Auth-* gateway headers.
 */
public final class UserContext {

    private UserContext() {}

    public static String getUsername() {
        var details = getDetails();
        return details != null ? details.username() : "system";
    }

    public static Long getUserId() {
        var details = getDetails();
        if (details == null || details.userId() == null) return 0L;
        try {
            return Long.parseLong(details.userId());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public static String getRole() {
        var details = getDetails();
        return details != null ? details.role() : "ANONYMOUS";
    }

    public static String getCorrelationId() {
        var details = getDetails();
        return details != null ? details.correlationId() : null;
    }

    private static HeaderAuthenticationFilter.UserContextDetails getDetails() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null &&
                auth.getDetails() instanceof HeaderAuthenticationFilter.UserContextDetails d) {
            return d;
        }
        return null;
    }
}