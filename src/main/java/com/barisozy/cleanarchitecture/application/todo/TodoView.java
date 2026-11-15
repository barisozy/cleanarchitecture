package com.barisozy.cleanarchitecture.application.todo;

import com.barisozy.cleanarchitecture.domain.todo.Todo;
import com.barisozy.cleanarchitecture.domain.todo.TodoStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A todo as the application exposes it. */
public record TodoView(
        UUID id,
        UUID ownerId,
        String title,
        @Nullable String description,
        TodoStatus status,
        @Nullable LocalDate dueDate,
        Instant createdAt,
        Instant updatedAt,
        @Nullable Instant completedAt) {

    public static TodoView from(Todo todo) {
        return new TodoView(
                todo.id().value(),
                todo.ownerId().value(),
                todo.title().value(),
                todo.description() == null ? null : todo.description().value(),
                todo.status(),
                todo.dueDate(),
                todo.createdAt(),
                todo.updatedAt(),
                todo.completedAt());
    }
}
