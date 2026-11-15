package com.barisozy.cleanarchitecture.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.barisozy.cleanarchitecture.application.security.AuthenticatedUser;
import com.barisozy.cleanarchitecture.domain.user.RoleName;
import com.barisozy.cleanarchitecture.fixtures.UserTestData;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** Covers the role-claim translation of the access token. */
class JwtAuthenticationConverterTest {

    private final JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

    @Test
    void mapsTheSingleRoleToItsAuthorityAndTheClaimsToThePrincipal() {
        UUID subject = UserTestData.USER_ID.value();

        AbstractAuthenticationToken authentication =
                converter.convert(token(subject.toString(), List.of("ADMIN")).build());

        assertThat(authentication.getName()).isEqualTo(subject.toString());
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
        assertThat(authentication.getPrincipal())
                .isEqualTo(new AuthenticatedUser(UserTestData.USER_ID, RoleName.ADMIN));
    }

    private static Jwt.Builder token(String subject, List<?> roles) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .claim("roles", roles)
                .issuedAt(Instant.parse("2026-11-04T10:00:00Z"))
                .expiresAt(Instant.parse("2026-11-04T10:15:00Z"));
    }
}
