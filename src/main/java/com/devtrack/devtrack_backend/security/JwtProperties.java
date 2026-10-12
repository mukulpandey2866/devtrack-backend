package com.devtrack.devtrack_backend.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, String issuer, String audience, Duration accessTokenTtl) {

    private static final int MIN_SECRET_BYTES = 32;
    private static final Duration MIN_TTL = Duration.ofMinutes(1);
    private static final Duration MAX_TTL = Duration.ofHours(24);

    public JwtProperties {
        if (secret == null || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "Configuration error: JWT_SECRET (app.jwt.secret) must be set and at least 32 bytes long");
        }
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
            throw new IllegalStateException("Configuration error: app.jwt.issuer and app.jwt.audience must not be blank");
        }
        if (accessTokenTtl == null || accessTokenTtl.compareTo(MIN_TTL) < 0 || accessTokenTtl.compareTo(MAX_TTL) > 0) {
            throw new IllegalStateException(
                    "Configuration error: JWT_ACCESS_TOKEN_TTL (app.jwt.access-token-ttl) must be between 1m and 24h");
        }
    }
}
