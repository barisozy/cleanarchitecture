package com.barisozy.cleanarchitecture.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.barisozy.cleanarchitecture.application.auth.AuthResult;
import com.barisozy.cleanarchitecture.domain.user.RoleName;
import com.barisozy.cleanarchitecture.domain.user.User;
import com.barisozy.cleanarchitecture.fixtures.AuthTestData;
import com.barisozy.cleanarchitecture.fixtures.FixedClock;
import com.barisozy.cleanarchitecture.fixtures.UserTestData;
import com.barisozy.cleanarchitecture.infrastructure.config.properties.JwtProperties;
import java.time.Instant;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtTokenIssuerTest {

    private FixedClock clock;
    private JwtDecoder decoder;
    private JwtTokenIssuer issuer;

    @BeforeEach
    void setUp() {
        clock = new FixedClock(UserTestData.NOW);
        decoder = decoder(clock);
        issuer = new JwtTokenIssuer(encoder(), properties(), clock);
    }

    @Test
    void signsTheTokenWithHs256AndTheClaimSet() {
        User user = UserTestData.withFixedId();

        AuthResult result = issuer.issue(user);
        Jwt jwt = decoder.decode(result.accessToken());

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresIn()).isEqualTo(AuthTestData.TTL.toSeconds());
        assertThat(result.user().id()).isEqualTo(user.id());
        assertThat(result.user().email()).isEqualTo(UserTestData.USER_EMAIL);
        assertThat(result.user().role()).isEqualTo(RoleName.USER);

        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256").containsEntry("typ", "JWT");
        assertThat(jwt.getIssuer()).hasToString(AuthTestData.ISSUER);
        assertThat(jwt.getAudience()).containsExactly(AuthTestData.AUDIENCE);
        assertThat(jwt.getSubject()).isEqualTo(user.id().value().toString());
        assertThat(jwt.getIssuedAt()).isEqualTo(UserTestData.NOW);
        assertThat(jwt.getNotBefore()).isEqualTo(UserTestData.NOW);
        assertThat(jwt.getExpiresAt()).isEqualTo(UserTestData.NOW.plus(AuthTestData.TTL));
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly(RoleName.USER.name());
        assertThat(decoder.decode(issuer.issue(UserTestData.admin()).accessToken())
                        .getClaimAsStringList("roles"))
                .containsExactly(RoleName.ADMIN.name());

        // Every timestamp comes from the injected clock, not from the system clock.
        Instant later = UserTestData.NOW.plusSeconds(3600);
        clock.setInstant(later);

        assertThat(decoder.decode(issuer.issue(user).accessToken()).getIssuedAt())
                .isEqualTo(later);
    }

    @Test
    void carriesNeitherAJtiNorAnEmailClaim() {
        Jwt jwt = decoder.decode(issuer.issue(UserTestData.withFixedId()).accessToken());

        assertThat(jwt.getClaims()).doesNotContainKeys("jti", "email");
    }

    private static JwtProperties properties() {
        return new JwtProperties(AuthTestData.ISSUER, AuthTestData.AUDIENCE, AuthTestData.TTL, AuthTestData.SECRET);
    }

    private static JwtEncoder encoder() {
        return NimbusJwtEncoder.withSecretKey(secretKey())
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    private static JwtDecoder decoder(FixedClock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator();
        timestamps.setClock(clock);
        decoder.setJwtValidator(timestamps);
        return decoder;
    }

    private static SecretKey secretKey() {
        return new SecretKeySpec(AuthTestData.keyBytes(), "HmacSHA256");
    }
}
