package com.barisozy.cleanarchitecture.application.security;

import static java.util.Objects.requireNonNull;

import com.barisozy.cleanarchitecture.domain.user.RoleName;
import com.barisozy.cleanarchitecture.domain.user.UserId;

/**
 * The authenticated principal, translated from a validated access token.
 *
 * <p>Application services never read Spring Security's context directly: the web layer translates
 * the token into this record, and the application layer only ever sees the identity and the single
 * role it was granted.
 */
public record AuthenticatedUser(UserId id, RoleName role) {

    public AuthenticatedUser {
        requireNonNull(id, "id");
        requireNonNull(role, "role");
    }

    public boolean isAdmin() {
        return role == RoleName.ADMIN;
    }
}
