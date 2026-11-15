package com.barisozy.cleanarchitecture.domain.todo;

import com.barisozy.cleanarchitecture.domain.DomainException;
import org.jspecify.annotations.Nullable;

/** Optional description; blank input is normalized to {@code null}. */
public record TodoDescription(String value) {

    private static final int MAX_LENGTH = 2000;

    public TodoDescription {
        if (value == null) {
            throw new DomainException("todo.description.invalid", "Description must not be null; use ofNullable.");
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new DomainException("todo.description.invalid", "Description must not be blank; use ofNullable.");
        }
        if (normalized.length() > MAX_LENGTH) {
            throw new DomainException("todo.description.invalid", "Description must not exceed 2000 characters.");
        }
        value = normalized;
    }

    public static @Nullable TodoDescription ofNullable(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new TodoDescription(value);
    }
}
