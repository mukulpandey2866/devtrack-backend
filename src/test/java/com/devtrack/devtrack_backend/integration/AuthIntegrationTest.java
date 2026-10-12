package com.devtrack.devtrack_backend.integration;

import com.devtrack.devtrack_backend.AbstractIntegrationTest;
import com.devtrack.devtrack_backend.support.ApiClient;
import com.devtrack.devtrack_backend.support.ApiClient.TestUser;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.ResultActions;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends AbstractIntegrationTest {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Test
    void registrationCreatesUserAndNeverExposesPassword() throws Exception {
        ResultActions result = api.post(null, "/api/auth/register", """
                {"fullName":"  Ada Lovelace ","email":"  Ada@Example.COM ","password":"%s"}""".formatted(ApiClient.PASSWORD));

        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.fullName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.platformRole").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        String stored = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = 'ada@example.com'", String.class);
        assertThat(stored).startsWith("$2").isNotEqualTo(ApiClient.PASSWORD);
    }

    @Test
    void registrationCannotAssignPrivilegedRole() throws Exception {
        api.post(null, "/api/auth/register", """
                {"fullName":"Mallory","email":"mallory@example.com","password":"%s","platformRole":"ADMIN"}"""
                .formatted(ApiClient.PASSWORD)).andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("SELECT platform_role FROM users WHERE email = 'mallory@example.com'", String.class))
                .isEqualTo("USER");
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
        String body = """
                {"fullName":"Ada","email":"%s","password":"%s"}""";
        api.post(null, "/api/auth/register", body.formatted("ada@example.com", ApiClient.PASSWORD))
                .andExpect(status().isCreated());

        api.post(null, "/api/auth/register", body.formatted("ADA@example.com", ApiClient.PASSWORD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void invalidRegistrationReturnsFieldErrors() throws Exception {
        api.post(null, "/api/auth/register", """
                {"fullName":"","email":"not-an-email","password":"short"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='fullName')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='email')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='password')]").exists());
    }

    @Test
    void passwordLongerThanBcryptLimitIsRejected() throws Exception {
        api.post(null, "/api/auth/register", """
                {"fullName":"Ada","email":"ada@example.com","password":"%s"}""".formatted("é".repeat(40)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedJsonReturnsBadRequestProblem() throws Exception {
        api.post(null, "/api/auth/register", "{not json")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void loginReturnsShortLivedBearerToken() throws Exception {
        TestUser user = api.registerAndLogin("Ada");

        api.post(null, "/api/auth/login", """
                {"email":"%s","password":"%s"}""".formatted(user.email().toUpperCase(), ApiClient.PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value(user.email()))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveIdenticalGenericFailure() throws Exception {
        TestUser user = api.registerAndLogin("Ada");

        String wrongPassword = api.post(null, "/api/auth/login", """
                {"email":"%s","password":"wrong-password-1"}""".formatted(user.email()))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknownEmail = api.post(null, "/api/auth/login", """
                {"email":"nobody@example.com","password":"wrong-password-1"}""")
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();

        assertThat(wrongPassword).isEqualTo(unknownEmail);
    }

    @Test
    void deactivatedUserCannotLogInOrUseExistingToken() throws Exception {
        TestUser user = api.registerAndLogin("Ada");
        jdbc.update("UPDATE users SET is_active = FALSE WHERE email = ?", user.email());

        api.post(null, "/api/auth/login", """
                {"email":"%s","password":"%s"}""".formatted(user.email(), ApiClient.PASSWORD))
                .andExpect(status().isUnauthorized());
        api.get(user.token(), "/api/auth/me").andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsAuthenticatedUserFromToken() throws Exception {
        TestUser user = api.registerAndLogin("Ada");

        api.get(user.token(), "/api/auth/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.id()))
                .andExpect(jsonPath("$.email").value(user.email()));
    }

    @Test
    void protectedEndpointsRejectMissingOrInvalidTokens() throws Exception {
        TestUser user = api.registerAndLogin("Ada");

        api.get(null, "/api/auth/me")
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        api.get(null, "/api/projects").andExpect(status().isUnauthorized());
        api.get("garbage.token.value", "/api/auth/me").andExpect(status().isUnauthorized());

        String tampered = user.token().substring(0, user.token().length() - 4) + "AAAA";
        api.get(tampered, "/api/auth/me").andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenWrongIssuerAndWrongAudienceAreRejected() throws Exception {
        TestUser user = api.registerAndLogin("Ada");
        Instant now = Instant.now();

        api.get(signedToken(user.id(), "devtrack-backend", "devtrack-frontend", now.minusSeconds(7200), now.minusSeconds(3600)),
                "/api/auth/me").andExpect(status().isUnauthorized());
        api.get(signedToken(user.id(), "someone-else", "devtrack-frontend", now, now.plusSeconds(600)),
                "/api/auth/me").andExpect(status().isUnauthorized());
        api.get(signedToken(user.id(), "devtrack-backend", "another-app", now, now.plusSeconds(600)),
                "/api/auth/me").andExpect(status().isUnauthorized());
        api.get(signedToken(user.id(), "devtrack-backend", "devtrack-frontend", now, now.plusSeconds(600)),
                "/api/auth/me").andExpect(status().isOk());
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() throws Exception {
        TestUser user = api.registerAndLogin("Ada");
        Instant now = Instant.now();
        String forged = signedToken("a-different-secret-that-is-long-enough-123456", user.id(), "devtrack-backend",
                "devtrack-frontend", now, now.plusSeconds(600));

        api.get(forged, "/api/auth/me").andExpect(status().isUnauthorized());
    }

    @Test
    void corsAllowsConfiguredOriginOnly() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/projects")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/projects")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    private String signedToken(String subject, String issuer, String audience, Instant issuedAt, Instant expiresAt) {
        return signedToken(jwtSecret, subject, issuer, audience, issuedAt, expiresAt);
    }

    private String signedToken(String secret, String subject, String issuer, String audience,
                               Instant issuedAt, Instant expiresAt) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience)).subject(subject)
                .issuedAt(issuedAt).expiresAt(expiresAt).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
