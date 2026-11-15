package com.barisozy.cleanarchitecture.domain.todo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.barisozy.cleanarchitecture.domain.DomainException;
import com.barisozy.cleanarchitecture.domain.user.UserId;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TodoTest {

    private static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");
    private static final UserId OWNER = UserId.newId();
    private static final UserId OTHER = UserId.newId();

    @Test
    void createStartsAPendingTodoForItsOwner() {
        Todo todo = Todo.create(
                OWNER,
                TodoTitle.of("Write plan"),
                TodoDescription.ofNullable("details"),
                LocalDate.of(2026, 11, 10),
                NOW);

        assertThat(todo.id()).isNotNull();
        assertThat(todo.ownerId()).isEqualTo(OWNER);
        assertThat(todo.title().value()).isEqualTo("Write plan");
        assertThat(todo.description()).isEqualTo(TodoDescription.ofNullable("details"));
        assertThat(todo.dueDate()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(todo.status()).isEqualTo(TodoStatus.PENDING);
        assertThat(todo.completedAt()).isNull();
        assertThat(todo.createdAt()).isEqualTo(NOW);
        assertThat(todo.updatedAt()).isEqualTo(NOW);

        // A blank description is stored as no description at all.
        Todo blank = Todo.create(OWNER, TodoTitle.of("Title"), TodoDescription.ofNullable("  "), null, NOW);
        assertThat(blank.description()).isNull();

        assertThat(todo.isOwnedBy(OWNER)).isTrue();
        assertThat(todo.isOwnedBy(OTHER)).isFalse();
    }

    @Test
    void updateReplacesTheContentAndKeepsTheCompletionState() {
        Todo todo = Todo.create(OWNER, TodoTitle.of("Old"), null, null, NOW);
        Instant later = NOW.plusSeconds(60);

        Todo updated = todo.update(
                TodoTitle.of("New"), TodoDescription.ofNullable("details"), LocalDate.of(2026, 11, 10), later);

        assertThat(updated.title().value()).isEqualTo("New");
        assertThat(updated.description()).isEqualTo(TodoDescription.ofNullable("details"));
        assertThat(updated.dueDate()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(updated.updatedAt()).isEqualTo(later);
        assertThat(updated.createdAt()).isEqualTo(NOW);

        Todo completed = todo.complete(NOW).update(TodoTitle.of("New"), null, null, later);
        assertThat(completed.status()).isEqualTo(TodoStatus.COMPLETED);
        assertThat(completed.completedAt()).isEqualTo(NOW);
        assertThat(completed.updatedAt()).isEqualTo(later);
    }

    @Test
    void completeSetsTheCompletionStateAndTheInvariantIsEnforced() {
        Todo todo = Todo.create(OWNER, TodoTitle.of("Title"), null, null, NOW);
        Instant later = NOW.plusSeconds(60);

        Todo completed = todo.complete(later);

        assertThat(completed.status()).isEqualTo(TodoStatus.COMPLETED);
        assertThat(completed.completedAt()).isEqualTo(later);
        assertThat(completed.updatedAt()).isEqualTo(later);
        assertThat(completed.createdAt()).isEqualTo(NOW);

        // A completed todo is never reopened.
        assertThatThrownBy(() -> completed.complete(later))
                .isInstanceOf(DomainException.class)
                .satisfies(exception ->
                        assertThat(((DomainException) exception).code()).isEqualTo("todo.already-completed"))
                .hasMessage("Todo is already completed.");

        // The status and the completion timestamp cannot drift apart: a completed todo without a
        // timestamp and a pending todo with one are both rejected.
        assertThatThrownBy(() -> new Todo(
                        TodoId.newId(), OWNER, TodoTitle.of("Title"), null, null, TodoStatus.COMPLETED, null, NOW, NOW))
                .isInstanceOf(DomainException.class)
                .satisfies(exception ->
                        assertThat(((DomainException) exception).code()).isEqualTo("todo.completion-time.invalid"));

        assertThatThrownBy(() -> new Todo(
                        TodoId.newId(), OWNER, TodoTitle.of("Title"), null, null, TodoStatus.PENDING, NOW, NOW, NOW))
                .isInstanceOf(DomainException.class)
                .satisfies(exception ->
                        assertThat(((DomainException) exception).code()).isEqualTo("todo.completion-time.invalid"));
    }
}
