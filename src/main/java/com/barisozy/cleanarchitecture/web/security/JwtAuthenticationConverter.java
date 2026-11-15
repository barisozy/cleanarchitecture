package com.barisozy.cleanarchitecture.web.security;

import com.barisozy.cleanarchitecture.application.security.AuthenticatedUser;
import com.barisozy.cleanarchitecture.domain.user.RoleName;
import com.barisozy.cleanarchitecture.domain.user.UserId;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Translates the validated token claims into Spring Security authorities and into the application
 * principal.
 *
 * <p>The decoder enforces the claim contract — one known role, a UUID subject — while it validates
 * the token, so this class only translates. The authentication carries the {@link AuthenticatedUser}
 * as its principal, which lets a controller declare {@code @AuthenticationPrincipal AuthenticatedUser}
 * instead of translating the token again.
 */
public class JwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        AuthenticatedUser user = toAuthenticatedUser(jwt);
        return new JwtAuthenticationToken(
                jwt,
                user,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
    }

    /** Maps the claims of an already validated token onto the application principal. */
    private static AuthenticatedUser toAuthenticatedUser(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return new AuthenticatedUser(UserId.from(jwt.getSubject()), RoleName.valueOf(roles.getFirst()));
    }
}
