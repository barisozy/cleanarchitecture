package com.barisozy.cleanarchitecture.infrastructure.persistence.todo;

import com.barisozy.cleanarchitecture.domain.todo.TodoStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The persistence representation of a todo.
 *
 * <p>The entity is a storage model, not the domain model: it mirrors the table, it carries no
 * behaviour and it is never handed to the application layer. The {@code version} column carries
 * the optimistic lock; Hibernate increments it on every write.
 */
@Entity
@Table(name = "todos")
public class TodoJpaEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private @Nullable String description;

    @Column(name = "due_date")
    private @Nullable LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TodoStatus status;

    @Column(name = "completed_at")
    private @Nullable Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * The revision of the row.
     *
     * <p>The property is nullable so that Spring Data recognises a new entity and stores it with
     * an insert instead of a merge. Hibernate assigns {@code 0} when the row is written.
     */
    @Version
    @Column(nullable = false)
    private @Nullable Long version;

    protected TodoJpaEntity() {}

    public TodoJpaEntity(
            UUID id,
            UUID ownerId,
            String title,
            @Nullable String description,
            @Nullable LocalDate dueDate,
            TodoStatus status,
            @Nullable Instant completedAt,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.title = title;
        this.description = description;
        this.dueDate = dueDate;
        this.status = status;
        this.completedAt = completedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getTitle() {
        return title;
    }

    public @Nullable String getDescription() {
        return description;
    }

    public @Nullable LocalDate getDueDate() {
        return dueDate;
    }

    public TodoStatus getStatus() {
        return status;
    }

    public @Nullable Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    void applyTitle(String value) {
        this.title = value;
    }

    void applyDescription(@Nullable String value) {
        this.description = value;
    }

    void applyDueDate(@Nullable LocalDate value) {
        this.dueDate = value;
    }

    void applyStatus(TodoStatus value) {
        this.status = value;
    }

    void applyCompletedAt(@Nullable Instant value) {
        this.completedAt = value;
    }

    void applyUpdatedAt(Instant value) {
        this.updatedAt = value;
    }
}
