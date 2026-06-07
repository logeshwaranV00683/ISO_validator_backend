package com.verinite.common.security;

/**
 * ThreadLocal holder — populated by HeaderAuthenticationFilter.
 * Services read this instead of re-parsing headers themselves.
 */
public class UserContext {

    private static final ThreadLocal<UserContext> HOLDER = new ThreadLocal<>();

    private String userId;
    private String username;
    private String role;
    private String correlationId;

    private UserContext() {}

    public static void set(String userId, String username, String role, String correlationId) {
        UserContext ctx = new UserContext();
        ctx.userId = userId;
        ctx.username = username;
        ctx.role = role;
        ctx.correlationId = correlationId;
        HOLDER.set(ctx);
    }

    public static UserContext get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public String getUserId()       { return userId; }
    public String getUsername()     { return username; }
    public String getRole()         { return role; }
    public String getCorrelationId(){ return correlationId; }
}