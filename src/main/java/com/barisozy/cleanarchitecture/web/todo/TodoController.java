package com.barisozy.cleanarchitecture.web.todo;

import com.barisozy.cleanarchitecture.application.PageResult;
import com.barisozy.cleanarchitecture.application.security.AuthenticatedUser;
import com.barisozy.cleanarchitecture.application.todo.TodoService;
import com.barisozy.cleanarchitecture.application.todo.TodoView;
import com.barisozy.cleanarchitecture.web.pagination.PageResponse;
import com.barisozy.cleanarchitecture.web.todo.dto.CreateTodoRequest;
import com.barisozy.cleanarchitecture.web.todo.dto.TodoResponse;
import com.barisozy.cleanarchitecture.web.todo.dto.UpdateTodoRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter of the todo feature.
 *
 * <p>The controller only translates HTTP into service calls. The application enforces ownership;
 * this layer enforces the coarse "authenticated" rule for the whole resource.
 */
@RestController
@RequestMapping("/api/todos")
@Tag(name = "Todos", description = "Todo management")
@PreAuthorize("isAuthenticated()")
public class TodoController {

    private final TodoService todoService;

    public TodoController(TodoService todoService) {
        this.todoService = todoService;
    }

    @PostMapping
    @Operation(summary = "Create a todo")
    @ApiResponse(responseCode = "201", description = "Todo created")
    public ResponseEntity<TodoResponse> create(
            @AuthenticationPrincipal AuthenticatedUser caller, @Valid @RequestBody CreateTodoRequest request) {
        TodoView created = todoService.create(caller, request.title(), request.description(), request.dueDate());
        TodoResponse body = TodoResponse.from(created);
        return ResponseEntity.created(URI.create("/api/todos/" + body.id())).body(body);
    }

    @GetMapping
    @Operation(
            summary = "List todos",
            description = "Returns a page of todos owned by the caller. Administrators see all todos.")
    @ApiResponse(responseCode = "200", description = "Todo page")
    public ResponseEntity<PageResponse<TodoResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (1-100)") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "Filter by completion state") @RequestParam(required = false) @Nullable Boolean completed) {
        PageResult<TodoView> result = todoService.list(caller, completed, page, size);
        return ResponseEntity.ok(PageResponse.from(result, TodoResponse::from));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a todo")
    @ApiResponse(responseCode = "200", description = "Todo found")
    public ResponseEntity<TodoResponse> get(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return ResponseEntity.ok(TodoResponse.from(todoService.get(caller, id)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a todo")
    @ApiResponse(responseCode = "200", description = "Todo updated")
    public ResponseEntity<TodoResponse> update(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTodoRequest request) {
        TodoView updated = todoService.update(caller, id, request.title(), request.description(), request.dueDate());
        return ResponseEntity.ok(TodoResponse.from(updated));
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Complete a todo")
    @ApiResponse(responseCode = "200", description = "Todo completed")
    public ResponseEntity<TodoResponse> complete(
            @AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return ResponseEntity.ok(TodoResponse.from(todoService.complete(caller, id)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a todo")
    @ApiResponse(responseCode = "204", description = "Todo deleted")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        todoService.delete(caller, id);
        return ResponseEntity.noContent().build();
    }
}
