package com.barisozy.cleanarchitecture.infrastructure.persistence.todo;

import com.barisozy.cleanarchitecture.application.todo.TodoView;
import com.barisozy.cleanarchitecture.domain.todo.Todo;
import com.barisozy.cleanarchitecture.domain.todo.TodoDescription;
import com.barisozy.cleanarchitecture.domain.todo.TodoId;
import com.barisozy.cleanarchitecture.domain.todo.TodoTitle;
import com.barisozy.cleanarchitecture.domain.user.UserId;
import org.springframework.stereotype.Component;

/**
 * Maps between the todo aggregate and its persistence representation.
 *
 * <p>The mapper translates values; it never re-applies a business rule.
 */
@Component
public class TodoJpaMapper {

    public Todo toDomain(TodoJpaEntity entity) {
        return new Todo(
                new TodoId(entity.getId()),
                new UserId(entity.getOwnerId()),
                new TodoTitle(entity.getTitle()),
                TodoDescription.ofNullable(entity.getDescription()),
                entity.getDueDate(),
                entity.getStatus(),
                entity.getCompletedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    /** Projects a stored row onto the read model without rehydrating the aggregate. */
    public TodoView toView(TodoJpaEntity entity) {
        return new TodoView(
                entity.getId(),
                entity.getOwnerId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getDueDate(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getCompletedAt());
    }

    /** Creates a new entity; the revision stays unset so that the write becomes an insert. */
    public TodoJpaEntity toEntity(Todo todo) {
        return new TodoJpaEntity(
                todo.id().value(),
                todo.ownerId().value(),
                todo.title().value(),
                todo.description() == null ? null : todo.description().value(),
                todo.dueDate(),
                todo.status(),
                todo.completedAt(),
                todo.createdAt(),
                todo.updatedAt());
    }

    /**
     * Copies the state of the aggregate onto an already managed entity.
     *
     * <p>The revision is deliberately not copied: the persistence provider owns it, and it is the
     * value the optimistic lock is checked against.
     */
    public void apply(TodoJpaEntity entity, Todo todo) {
        entity.applyTitle(todo.title().value());
        entity.applyDescription(
                todo.description() == null ? null : todo.description().value());
        entity.applyDueDate(todo.dueDate());
        entity.applyStatus(todo.status());
        entity.applyCompletedAt(todo.completedAt());
        entity.applyUpdatedAt(todo.updatedAt());
    }
}
