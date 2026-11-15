package com.barisozy.cleanarchitecture.domain.user;

import static java.util.Objects.requireNonNull;

import java.time.Instant;

/**
 * The user aggregate.
 *
 * <p>The aggregate is insert-only: a user is created by the registration flow and is never mutated
 * afterwards. The account state therefore carries no {@code enabled} flag, no {@code updatedAt}
 * timestamp and no optimistic-lock version — there is nothing to change, so there is nothing to
 * guard. A feature that needs to change a password, disable an account or switch roles introduces
 * that state explicitly instead of keeping an unused column around.
 */
public record User(UserId id, Email email, PasswordHash passwordHash, RoleName role, Instant createdAt) {

    public User {
        requireNonNull(id, "id");
        requireNonNull(email, "email");
        requireNonNull(passwordHash, "passwordHash");
        requireNonNull(role, "role");
        requireNonNull(createdAt, "createdAt");
    }

    /** Creates a new user with a fresh identity. */
    public static User register(Email email, PasswordHash passwordHash, RoleName role, Instant now) {
        return new User(UserId.newId(), email, passwordHash, role, now);
    }
}
