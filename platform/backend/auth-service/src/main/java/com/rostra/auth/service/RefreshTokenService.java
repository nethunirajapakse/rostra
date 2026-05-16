package com.rostra.auth.service;

import com.rostra.auth.entity.RefreshToken;
import com.rostra.auth.entity.User;
import com.rostra.auth.domain.repository.RefreshTokenRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.refresh-token.expiry-days}")
    private int expiryDays;

    // ── Issue ────────────────────────────────────────────────────────────────

    @Transactional
    public String issueRefreshToken(User user) {
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = hash(rawToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(expiryDays, ChronoUnit.DAYS))
                .build();

        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    // ── Rotate ───────────────────────────────────────────────────────────────

    @Transactional
    public String rotate(String rawToken) {
        String incomingHash = hash(rawToken);

        RefreshToken existing = refreshTokenRepository.findByTokenHash(incomingHash)
                .orElseThrow(() -> new RuntimeException("Refresh token not found"));

        // Reuse detection — token already rotated, someone is replaying an old token
        if (existing.isRevoked()) {
            refreshTokenRepository.revokeAllByUserId(existing.getUser().getId());
            throw new RuntimeException("Refresh token reuse detected — all sessions revoked");
        }

        if (existing.isExpired()) {
            throw new RuntimeException("Refresh token expired");
        }

        // Issue new token
        String newRawToken = UUID.randomUUID().toString();
        String newHash = hash(newRawToken);

        RefreshToken newToken = RefreshToken.builder()
                .user(existing.getUser())
                .tokenHash(newHash)
                .expiresAt(Instant.now().plus(expiryDays, ChronoUnit.DAYS))
                .build();

        newToken = refreshTokenRepository.save(newToken);

        // Revoke old token, link to replacement
        existing.setRevoked(true);
        existing.setRevokedAt(Instant.now());
        existing.setReplacedBy(newToken.getId());
        refreshTokenRepository.save(existing);

        return newRawToken;
    }

    // ── Revoke all (logout) ──────────────────────────────────────────────────

    @Transactional
    public void revokeAll(String rawToken) {
        String tokenHash = hash(rawToken);
        refreshTokenRepository.findByTokenHash(tokenHash)
                .ifPresent(rt -> refreshTokenRepository.revokeAllByUserId(rt.getUser().getId()));
    }

    // ── Get user from token ──────────────────────────────────────────────────

    public User getUserFromToken(String rawToken) {
        String tokenHash = hash(rawToken);
        return refreshTokenRepository.findByTokenHash(tokenHash)
                .map(RefreshToken::getUser)
                .orElseThrow(() -> new RuntimeException("Refresh token not found"));
    }

    // ── Hash ─────────────────────────────────────────────────────────────────

    public String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes());
            return Base64.getEncoder().encodeToString(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}

