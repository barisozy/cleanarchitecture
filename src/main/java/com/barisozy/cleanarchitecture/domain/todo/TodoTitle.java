package com.barisozy.cleanarchitecture.domain.todo;

import com.barisozy.cleanarchitecture.domain.DomainException;

public record TodoTitle(String value) {

    private static final int MAX_LENGTH = 200;

    public TodoTitle {
        if (value == null) {
            throw new DomainException("todo.title.invalid", "Title is required.");
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > MAX_LENGTH) {
            throw new DomainException("todo.title.invalid", "Title must be between 1 and 200 characters.");
        }
        value = normalized;
    }

    public static TodoTitle of(String value) {
        return new TodoTitle(value);
    }
}
