package com.barisozy.cleanarchitecture.domain.todo;

import com.barisozy.cleanarchitecture.domain.DomainException;
import java.util.UUID;

/** The identity of a {@link Todo}. */
public record TodoId(UUID value) {

    public TodoId {
        if (value == null) {
            throw new DomainException("todo.id.required", "Todo id is required.");
        }
    }

    public static TodoId newId() {
        return new TodoId(UUID.randomUUID());
    }

    /**
     * Parses a canonical UUID.
     *
     * @throws DomainException when the value is not a UUID
     */
    public static TodoId from(String value) {
        try {
            return new TodoId(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            throw new DomainException("todo.id.invalid", "Todo id must be a UUID.", exception);
        }
    }
}
