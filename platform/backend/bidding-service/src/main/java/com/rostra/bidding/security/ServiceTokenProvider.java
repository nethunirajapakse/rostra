package com.rostra.bidding.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Mints the short-lived credential bidding-service uses when it calls auction-service on its own behalf.
 * It is a JWT with type=SERVICE and subject=bidding-service, so it can never be mistaken for a user token
 * (user filters only accept type=ACCESS) and the user's own token is no longer forwarded between services.
 */
@Component
public class ServiceTokenProvider {

    public static final String SERVICE_NAME = "bidding-service";
    public static final String HEADER = "X-Service-Token";
    private static final Duration TTL = Duration.ofSeconds(60);

    private final SecretKey signingKey;

    public ServiceTokenProvider(@Value("${app.jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String mint() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(SERVICE_NAME)
                .claim("type", "SERVICE")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(TTL)))
                .signWith(signingKey)
                .compact();
    }
}
