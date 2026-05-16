package com.rostra.auth.dto;

import java.util.UUID;

public record AuthResponseDTO (
        String accessToken,
        String tokenType,
        UUID userId,
        String email,
        String displayName
) {
    public static AuthResponseDTO of(String accessToken, UUID userId, String email, String displayName) {
        return new AuthResponseDTO(accessToken, "Bearer", userId, email, displayName);
    }
}