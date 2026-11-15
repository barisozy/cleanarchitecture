package com.barisozy.cleanarchitecture.infrastructure.security;

import com.barisozy.cleanarchitecture.application.auth.AuthResult;
import com.barisozy.cleanarchitecture.application.auth.TokenIssuer;
import com.barisozy.cleanarchitecture.application.auth.UserView;
import com.barisozy.cleanarchitecture.domain.user.User;
import com.barisozy.cleanarchitecture.infrastructure.config.properties.JwtProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Issues the signed access token with Spring Security's {@link JwtEncoder}.
 *
 * <p>The claim contract is {@code iss}, {@code aud}, {@code sub} (the user id), {@code iat},
 * {@code nbf}, {@code exp} and {@code roles}. There is no {@code jti}, because there is no
 * revocation list that could use it, and no {@code email}, because personally identifiable
 * information does not belong in a credential.
 */
@Component
public class JwtTokenIssuer implements TokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtTokenIssuer(JwtEncoder jwtEncoder, JwtProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public AuthResult issue(User user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .subject(user.id().value().toString())
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .claim("roles", List.of(user.role().name()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String token =
                jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AuthResult(token, "Bearer", properties.accessTokenTtl().toSeconds(), UserView.from(user));
    }
}
