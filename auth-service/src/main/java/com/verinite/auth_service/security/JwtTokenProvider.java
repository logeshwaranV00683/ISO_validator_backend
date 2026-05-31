package com.verinite.auth_service.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtTokenProvider {

    @Value("${jwt.private-key-path}")
    private String privateKeyPath;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    public String generateToken(
            String username, String role, String jti) throws Exception {

        PrivateKey privateKey = loadPrivateKey();

        return Jwts.builder()
                .setSubject(username)
                .claim("role", role)
                .setId(jti)
                .setIssuedAt(new Date())
                .setExpiration(new Date(
                        System.currentTimeMillis() + expirationMs))
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
    }

    private PrivateKey loadPrivateKey() throws Exception {
        String content = new String(
                Files.readAllBytes(Paths.get(privateKeyPath)));
        String clean = content
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] keyBytes = Base64.getDecoder().decode(clean);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }
}