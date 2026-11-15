package com.barisozy.cleanarchitecture.application.auth;

import static java.util.Objects.requireNonNull;

/**
 * The outcome of a successful authentication: the access token and the authenticated user.
 *
 * <p>The access token is a credential. {@link #toString()} therefore never renders it, so that the
 * result can be logged without leaking a usable secret.
 */
public record AuthResult(String accessToken, String tokenType, long expiresIn, UserView user) {

    private static final String REDACTED = "********";

    public AuthResult {
        requireNonNull(accessToken, "accessToken");
        requireNonNull(tokenType, "tokenType");
        requireNonNull(user, "user");
        if (expiresIn <= 0) {
            throw new IllegalArgumentException("expiresIn must be positive");
        }
    }

    @Override
    public String toString() {
        return "AuthResult[accessToken=" + REDACTED + ", tokenType=" + tokenType + ", expiresIn=" + expiresIn
                + ", userId=" + user.id() + "]";
    }
}
