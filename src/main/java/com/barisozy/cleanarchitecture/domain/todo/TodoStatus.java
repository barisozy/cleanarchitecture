package com.barisozy.cleanarchitecture.domain.todo;

/**
 * The completion state of a todo.
 *
 * <p>{@code PENDING} is the state of a todo that was created but not completed yet. The status is
 * the single source of truth for the completion state; the API derives its {@code completed} flag
 * from it.
 */
public enum TodoStatus {
    PENDING,
    COMPLETED
}
