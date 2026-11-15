package com.barisozy.cleanarchitecture.domain.todo;

import static java.util.Objects.requireNonNull;

import com.barisozy.cleanarchitecture.domain.DomainException;
import com.barisozy.cleanarchitecture.domain.user.UserId;
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * The todo aggregate. Every mutation returns a new instance, so a state transition is always an
 * explicit expression of the business rule it implements.
 *
 * <p>The completion state is expressed by two fields that are bound by one invariant: {@code status
 * == COMPLETED} if and only if {@code completedAt != null}. The status names the state and is the
 * natural key for filtering and indexing; the timestamp records when the transition happened.
 * The invariant is enforced here and mirrored by a database check constraint, so the two fields
 * cannot drift apart.
 *
 * <p>The aggregate carries no revision. Optimistic locking is a persistence concern and lives on
 * the JPA entity; exposing a version here would suggest that the domain interprets it.
 */
public record Todo(
        TodoId id,
        UserId ownerId,
        TodoTitle title,
        @Nullable TodoDescription description,
        @Nullable LocalDate dueDate,
        TodoStatus status,
        @Nullable Instant completedAt,
        Instant createdAt,
        Instant updatedAt) {

    public Todo {
        requireNonNull(id, "id");
        requireNonNull(ownerId, "ownerId");
        requireNonNull(title, "title");
        requireNonNull(status, "status");
        requireNonNull(createdAt, "createdAt");
        requireNonNull(updatedAt, "updatedAt");
        if ((status == TodoStatus.COMPLETED) != (completedAt != null)) {
            throw new DomainException(
                    "todo.completion-time.invalid", "Completion time must be set exactly when the todo is completed.");
        }
    }

    /** Creates a pending todo. A todo is always created pending and can never be created completed. */
    public static Todo create(
            UserId ownerId,
            TodoTitle title,
            @Nullable TodoDescription description,
            @Nullable LocalDate dueDate,
            Instant now) {
        return new Todo(TodoId.newId(), ownerId, title, description, dueDate, TodoStatus.PENDING, null, now, now);
    }

    /**
     * Replaces the editable content of the todo.
     *
     * <p>The completion state is preserved: a completed todo stays completed.
     */
    public Todo update(
            TodoTitle title, @Nullable TodoDescription description, @Nullable LocalDate dueDate, Instant now) {
        return new Todo(id, ownerId, title, description, dueDate, status, completedAt, createdAt, now);
    }

    /** Completes the todo. A completed todo is never reopened. */
    public Todo complete(Instant now) {
        if (status == TodoStatus.COMPLETED) {
            throw new DomainException("todo.already-completed", "Todo is already completed.");
        }
        return new Todo(id, ownerId, title, description, dueDate, TodoStatus.COMPLETED, now, createdAt, now);
    }

    public boolean isOwnedBy(UserId userId) {
        return ownerId.equals(userId);
    }
}
