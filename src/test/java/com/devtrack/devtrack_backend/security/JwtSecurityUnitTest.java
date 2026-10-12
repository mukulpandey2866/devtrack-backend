package com.devtrack.devtrack_backend.security;

import com.devtrack.devtrack_backend.config.SecurityConfig;
import com.devtrack.devtrack_backend.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtSecurityUnitTest {

    private static final String SECRET = "unit-test-secret-0123456789-0123456789-abc";

    private JwtProperties properties(String secret, Duration ttl) {
        return new JwtProperties(secret, "devtrack-backend", "devtrack-frontend", ttl);
    }

    private User userWithId(UUID id) throws Exception {
        User user = new User("Ada", "ada@example.com", "hash");
        Field field = User.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(user, id);
        return user;
    }

    @Test
    void issuedTokenCarriesConfiguredClaimsAndExpiry() throws Exception {
        Clock clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
        JwtProperties props = properties(SECRET, Duration.ofMinutes(10));
        UUID userId = UUID.randomUUID();

        JwtService.IssuedToken token = new JwtService(props, clock).issueAccessToken(userWithId(userId));
        Jwt jwt = new SecurityConfig().jwtDecoder(props).decode(token.value());

        assertThat(token.expiresInSeconds()).isEqualTo(600);
        assertThat(jwt.getSubject()).isEqualTo(userId.toString());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("devtrack-backend");
        assertThat(jwt.getAudience()).containsExactly("devtrack-frontend");
        assertThat(jwt.getExpiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(10)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        assertThat(jwt.getClaims()).doesNotContainKeys("password", "passwordHash", "email");
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        Clock past = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
        JwtProperties props = properties(SECRET, Duration.ofMinutes(10));
        String token = new JwtService(props, past).issueAccessToken(userWithId(UUID.randomUUID())).value();
        JwtDecoder decoder = new SecurityConfig().jwtDecoder(props);

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(BadJwtException.class);
    }

    @Test
    void secretsAndTtlAreValidatedAtStartup() {
        assertThatThrownBy(() -> properties("too-short", Duration.ofMinutes(10)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> properties(null, Duration.ofMinutes(10)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> properties(SECRET, Duration.ofDays(30)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_ACCESS_TOKEN_TTL");
    }

    @Test
    void corsWildcardsAreRejectedAndBlankOriginsIgnored() {
        assertThatThrownBy(() -> new CorsProperties(java.util.List.of("*")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CorsProperties(java.util.List.of("https://*.example.com")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(new CorsProperties(java.util.List.of(" https://app.example.com ", "")).allowedOrigins())
                .containsExactly("https://app.example.com");
        assertThat(new CorsProperties(null).allowedOrigins()).isEmpty();
    }
}
