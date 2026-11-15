package com.barisozy.cleanarchitecture.infrastructure.security;

import com.barisozy.cleanarchitecture.domain.user.RoleName;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Validates the {@code roles} claim of an access token.
 *
 * <p>The template has exactly one role per user, so the claim must contain exactly one known role.
 * A missing, empty, duplicated or unknown role makes the token invalid: accepting it would either
 * produce an account without any authority or an account with more authority than it was granted.
 */
public final class JwtRolesValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_ROLES =
            new OAuth2Error("invalid_token", "The token must carry exactly one known role.", null);
    private static final Set<String> KNOWN_ROLES =
            Arrays.stream(RoleName.values()).map(Enum::name).collect(Collectors.toUnmodifiableSet());

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Object claim = token.getClaims().get("roles");
        if (!(claim instanceof List<?> roles) || roles.size() != 1) {
            return OAuth2TokenValidatorResult.failure(INVALID_ROLES);
        }
        if (!(roles.getFirst() instanceof String role) || !KNOWN_ROLES.contains(role)) {
            return OAuth2TokenValidatorResult.failure(INVALID_ROLES);
        }
        return OAuth2TokenValidatorResult.success();
    }
}
