package com.barisozy.cleanarchitecture.domain.todo;

import java.util.Optional;

/**
 * Persistence port of the {@link Todo} aggregate.
 *
 * <p>The write operations state their intent explicitly: {@link #insert(Todo)} stores a new
 * aggregate, {@link #update(Todo)} writes an existing one and {@link #delete(Todo)} removes it.
 * The adapter protects updates and deletes with the optimistic lock of the row, so a write that
 * loses a race is reported as a conflict instead of overwriting a concurrent change.
 */
public interface TodoRepository {

    Optional<Todo> findById(TodoId id);

    /** Stores a new aggregate and returns the stored state. */
    Todo insert(Todo todo);

    /**
     * Writes an existing aggregate.
     *
     * <p>The adapter applies the change to the managed row, so the persistence provider checks the
     * revision that was read in the current transaction and rejects a stale write with an
     * optimistic-lock failure.
     */
    Todo update(Todo todo);

    /**
     * Deletes an existing aggregate.
     *
     * <p>The deletion is protected by the same optimistic lock as {@link #update(Todo)}.
     */
    void delete(Todo todo);
}
