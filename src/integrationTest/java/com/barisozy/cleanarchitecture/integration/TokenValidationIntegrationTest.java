package com.barisozy.cleanarchitecture.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.barisozy.cleanarchitecture.infrastructure.config.properties.JwtProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Verifies the validation chain of the resource server: the timestamps, the issuer, the audience,
 * the subject and the role claim.
 *
 * <p>The tokens are minted with the encoder of the application, because the signing key of the test
 * profile is ephemeral and only the application knows it. The key is therefore always valid and
 * only the claim under test is wrong.
 */
class TokenValidationIntegrationTest extends AbstractIntegrationTest {

    private static final String PROTECTED_ENDPOINT = "/api/todos";

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    void acceptsAValidTokenForAProtectedEndpoint() {
        String email = uniqueEmail("valid-token");
        register(email);
        UUID userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);

        String token = mint(List.of("USER"), builder -> builder.subject(userId.toString()));

        MvcTestResult result = mvc.get()
                .uri(PROTECTED_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange();

        assertThat(result).hasStatusOk();
    }

    /** The lifetime, the issuer, the audience and the subject are all part of the validation. */
    @Test
    void rejectsATokenThatFailsTheValidation() {
        Instant past = Instant.now().minus(Duration.ofMinutes(10));

        assertUnauthorized(mint(
                List.of("USER"),
                builder -> builder.issuedAt(past).notBefore(past).expiresAt(past.plus(Duration.ofMinutes(5)))));
        assertUnauthorized(mint(List.of("USER"), builder -> builder.issuer("https://evil.example.com")));
        assertUnauthorized(mint(List.of("USER"), builder -> builder.audience(List.of("another-api"))));
        assertUnauthorized(mint(List.of("USER"), builder -> builder.subject("not-a-uuid")));
    }

    /** The role claim is mandatory and holds exactly one known role. */
    @Test
    void rejectsATokenWithAnInvalidRoleClaim() {
        assertUnauthorized(mint(null, builder -> {}));
        assertUnauthorized(mint(List.of("SUPERUSER"), builder -> {}));
        assertUnauthorized(mint(List.of("USER", "ADMIN"), builder -> {}));
    }

    /** A valid claim set signed by another key is not a token of this application. */
    @Test
    void rejectsATokenSignedWithAnotherKey() {
        SecretKey wrongKey = new SecretKeySpec(
                "another-32-byte-secret-key-for-hs256!!".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder wrongEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(wrongKey));

        assertUnauthorized(mint(wrongEncoder, List.of("USER"), builder -> {}));
    }

    private void assertUnauthorized(String token) {
        MvcTestResult result = mvc.get()
                .uri(PROTECTED_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange();

        assertThat(result)
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .hasHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .bodyJson()
                .extractingPath("$.code")
                .asString()
                .isEqualTo("security.unauthorized");
    }

    /**
     * Mints a token with the claims of the application and the given deviations.
     *
     * <p>The role claim is only added when the test asks for it: a token without the claim is
     * invalid, and that is one of the cases under test.
     */
    private String mint(@Nullable List<String> roles, Consumer<JwtClaimsSet.Builder> deviation) {
        return mint(jwtEncoder, roles, deviation);
    }

    private String mint(JwtEncoder encoder, @Nullable List<String> roles, Consumer<JwtClaimsSet.Builder> deviation) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .audience(List.of(jwtProperties.audience()))
                .subject(UUID.randomUUID().toString())
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(now.plus(jwtProperties.accessTokenTtl()));
        if (roles != null) {
            builder.claim("roles", roles);
        }
        deviation.accept(builder);
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(header, builder.build()))
                .getTokenValue();
    }
}
