package com.rostra.auction.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Verifies access tokens issued by the auth-service. Same shared secret (raw UTF-8 bytes), no call back to auth.
 * Revocation (signout) is enforced at the gateway; this checks signature, expiry and token type.
 */
@Service
public class JwtService {

    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "ACCESS";

    private final SecretKey signingKey;

    public JwtService(@Value("${app.jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Valid signature, not expired, and an ACCESS token (refresh tokens share the key and must not pass). */
    public boolean isValid(String token) {
        try {
            return TYPE_ACCESS.equals(parse(token).get(CLAIM_TYPE, String.class));
        } catch (Exception e) {
            return false;
        }
    }

    /** Valid signature, not expired, type=SERVICE and issued to the expected calling service. */
    public boolean isValidServiceToken(String token, String expectedService) {
        try {
            Claims claims = parse(token);
            return "SERVICE".equals(claims.get(CLAIM_TYPE, String.class))
                    && expectedService.equals(claims.getSubject());
        } catch (Exception e) {
            return false;
        }
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(parse(token).getSubject());
    }

    public String extractEmail(String token) {
        return parse(token).get("email", String.class);
    }
}
