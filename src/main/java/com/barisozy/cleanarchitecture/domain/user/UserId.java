package com.barisozy.cleanarchitecture.domain.user;

import com.barisozy.cleanarchitecture.domain.DomainException;
import java.util.UUID;

/** The identity of a {@link User}. */
public record UserId(UUID value) {

    public UserId {
        if (value == null) {
            throw new DomainException("user.id.required", "User id is required.");
        }
    }

    public static UserId newId() {
        return new UserId(UUID.randomUUID());
    }

    /**
     * Parses a canonical UUID.
     *
     * @throws DomainException when the value is not a UUID
     */
    public static UserId from(String value) {
        try {
            return new UserId(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            throw new DomainException("user.id.invalid", "User id must be a UUID.", exception);
        }
    }
}
