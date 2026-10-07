package com.rostra.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Edge-security settings, bound from {@code app.*} in application.yml.
 */
@ConfigurationProperties(prefix = "app")
public record EdgeSecurityProperties(Jwt jwt, @DefaultValue Cookie cookie) {

    /** HMAC secret shared with the auth-service (raw UTF-8 bytes, at least 32 bytes). */
    public record Jwt(String secret) {
    }

    public record Cookie(boolean secure) {
    }
}
