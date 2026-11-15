package com.barisozy.cleanarchitecture.infrastructure.security;

import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Validates that the {@code sub} claim is the identifier the application issues.
 *
 * <p>The subject is the user id, so it must be a UUID. Without this check a syntactically valid
 * token with an arbitrary subject would pass the decoder and fail later, in the controller, with
 * an internal error instead of a {@code 401}.
 */
public final class JwtSubjectValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_SUBJECT =
            new OAuth2Error("invalid_token", "The token must carry a valid subject.", null);

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String subject = token.getSubject();
        if (subject == null || !isUuid(subject)) {
            return OAuth2TokenValidatorResult.failure(INVALID_SUBJECT);
        }
        return OAuth2TokenValidatorResult.success();
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
