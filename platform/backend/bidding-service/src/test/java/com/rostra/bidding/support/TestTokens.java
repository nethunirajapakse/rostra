package com.rostra.bidding.support;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

/**
 * Signs the same kind of access token the auth-service issues, so tests go through the real JwtAuthFilter
 * and SecurityConfig instead of bypassing security with a mocked user.
 */
public final class TestTokens {

    /** Registered as app.jwt.secret by {@link PostgresIntegrationTest}. At least 32 bytes. */
    public static final String JWT_SECRET = "integration-test-secret-key-at-least-32-bytes-long";

    private TestTokens() {
    }

    public static String accessToken(UUID userId) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("type", "ACCESS")
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(15, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }
}
