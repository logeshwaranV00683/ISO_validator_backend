package com.verinite.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

import java.security.PublicKey;
import java.util.Date;

public class JwtUtil {
    // RS256 — use Public Key to verify (distributed to all services)
    // Use Private Key only in auth-service to sign

    public static Claims parseToken(String token, PublicKey publicKey) {

        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public static boolean isExpired(Claims claims) {
        return claims.getExpiration().before(new Date());
    }

    public static String extractRole(Claims claims) {
        return (String) claims.get("role");
    }

    public static String extractUsername(Claims claims) {
        return claims.getSubject();
    }
}