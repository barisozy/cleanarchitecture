package com.barisozy.cleanarchitecture.domain.user;

import java.util.Optional;

/**
 * Persistence port of the {@link User} aggregate.
 *
 * <p>The port is intentionally write-once: {@link #insert(User)} stores a new account. There is no
 * update operation because the aggregate has no mutable state.
 */
public interface UserRepository {

    Optional<User> findById(UserId id);

    Optional<User> findByEmail(Email email);

    /**
     * Stores a new aggregate.
     *
     * @throws com.barisozy.cleanarchitecture.application.ApplicationException with a
     *     {@code CONFLICT} type when the e-mail address is already registered
     */
    User insert(User user);
}
