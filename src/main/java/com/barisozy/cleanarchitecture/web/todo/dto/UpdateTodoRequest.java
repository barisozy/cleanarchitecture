package com.barisozy.cleanarchitecture.web.todo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

@Schema(description = "Full replacement payload for a todo")
public record UpdateTodoRequest(
        @Schema(description = "Todo title", example = "Write plan") @NotBlank @Size(max = 200) String title,

        @Schema(description = "Optional long description", example = "Finish the architecture document")
        @Size(max = 2000) @Nullable String description,

        @Schema(description = "Optional due date", example = "2026-11-10") @Nullable LocalDate dueDate) {}
