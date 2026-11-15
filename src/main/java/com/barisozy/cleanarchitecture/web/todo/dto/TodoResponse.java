package com.barisozy.cleanarchitecture.web.todo.dto;

import com.barisozy.cleanarchitecture.application.todo.TodoView;
import com.barisozy.cleanarchitecture.domain.todo.TodoStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The todo as the API exposes it.
 *
 * <p>The completion state is published as a single {@code completed} flag that is derived from the
 * status. The status itself is not published: two fields that describe the same state could
 * disagree, and the flag is the representation every client actually needs.
 */
@Schema(description = "Todo representation")
public record TodoResponse(
        @Schema(description = "Todo identifier") UUID id,

        @Schema(description = "Identifier of the user who owns the todo")
        UUID ownerId,

        @Schema(description = "Todo title", example = "Write plan")
        String title,

        @Schema(description = "Optional description") @Nullable String description,

        @Schema(description = "Whether the todo is completed")
        boolean completed,

        @Schema(description = "Optional due date") @Nullable LocalDate dueDate,

        @Schema(description = "Completion timestamp") @Nullable Instant completedAt,

        @Schema(description = "Creation timestamp") Instant createdAt,

        @Schema(description = "Last modification timestamp") Instant updatedAt) {

    public static TodoResponse from(TodoView view) {
        return new TodoResponse(
                view.id(),
                view.ownerId(),
                view.title(),
                view.description(),
                view.status() == TodoStatus.COMPLETED,
                view.dueDate(),
                view.completedAt(),
                view.createdAt(),
                view.updatedAt());
    }
}
