package com.barisozy.cleanarchitecture.application.todo;

import com.barisozy.cleanarchitecture.application.ApplicationException;
import com.barisozy.cleanarchitecture.application.PageResult;
import com.barisozy.cleanarchitecture.application.security.AuthenticatedUser;
import com.barisozy.cleanarchitecture.domain.todo.Todo;
import com.barisozy.cleanarchitecture.domain.todo.TodoDescription;
import com.barisozy.cleanarchitecture.domain.todo.TodoId;
import com.barisozy.cleanarchitecture.domain.todo.TodoRepository;
import com.barisozy.cleanarchitecture.domain.todo.TodoStatus;
import com.barisozy.cleanarchitecture.domain.todo.TodoTitle;
import com.barisozy.cleanarchitecture.domain.user.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The todo use cases.
 *
 * <p>The caller is always an {@link AuthenticatedUser}; the ownership rule is enforced here, at
 * the application boundary. A todo that does not exist and a todo of somebody else both produce
 * the same {@code 404}, so the API never discloses the existence of a resource.
 *
 * <p>The class is not final on purpose: {@link Transactional} is applied through a CGLIB proxy.
 */
@Service
public class TodoService {

    private final TodoRepository todoRepository;
    private final TodoQueryPort todoQueryPort;
    private final Clock clock;

    public TodoService(TodoRepository todoRepository, TodoQueryPort todoQueryPort, Clock clock) {
        this.todoRepository = todoRepository;
        this.todoQueryPort = todoQueryPort;
        this.clock = clock;
    }

    @Transactional
    public TodoView create(
            AuthenticatedUser caller, String title, @Nullable String description, @Nullable LocalDate dueDate) {
        Instant now = clock.instant();
        Todo todo =
                Todo.create(caller.id(), TodoTitle.of(title), TodoDescription.ofNullable(description), dueDate, now);
        return TodoView.from(todoRepository.insert(todo));
    }

    @Transactional(readOnly = true)
    public TodoView get(AuthenticatedUser caller, UUID todoId) {
        return TodoView.from(findReadable(caller, todoId));
    }

    /**
     * Lists the todos of the caller. A regular user sees its own todos, an administrator sees every
     * todo. A {@code null} completion filter means "any state".
     */
    @Transactional(readOnly = true)
    public PageResult<TodoView> list(AuthenticatedUser caller, @Nullable Boolean completed, int page, int size) {
        @Nullable UserId ownerId = caller.isAdmin() ? null : caller.id();
        @Nullable TodoStatus status = statusOf(completed);
        return todoQueryPort.findPage(ownerId, status, page, size);
    }

    @Transactional
    public TodoView update(
            AuthenticatedUser caller,
            UUID todoId,
            String title,
            @Nullable String description,
            @Nullable LocalDate dueDate) {
        Todo todo = findWritable(caller, todoId);
        Todo updated =
                todo.update(TodoTitle.of(title), TodoDescription.ofNullable(description), dueDate, clock.instant());
        return TodoView.from(todoRepository.update(updated));
    }

    @Transactional
    public TodoView complete(AuthenticatedUser caller, UUID todoId) {
        Todo todo = findWritable(caller, todoId);
        if (todo.status() == TodoStatus.COMPLETED) {
            throw ApplicationException.conflict("todo.already-completed", "Todo is already completed.");
        }
        return TodoView.from(todoRepository.update(todo.complete(clock.instant())));
    }

    @Transactional
    public void delete(AuthenticatedUser caller, UUID todoId) {
        todoRepository.delete(findWritable(caller, todoId));
    }

    /** Reads a todo the caller owns or, as an administrator, any todo. */
    private Todo findReadable(AuthenticatedUser caller, UUID todoId) {
        Todo todo = find(todoId);
        if (!todo.isOwnedBy(caller.id()) && !caller.isAdmin()) {
            throw notFound();
        }
        return todo;
    }

    /** Writes are owner-only, even for an administrator. */
    private Todo findWritable(AuthenticatedUser caller, UUID todoId) {
        Todo todo = find(todoId);
        if (!todo.isOwnedBy(caller.id())) {
            throw notFound();
        }
        return todo;
    }

    private Todo find(UUID todoId) {
        return todoRepository.findById(new TodoId(todoId)).orElseThrow(TodoService::notFound);
    }

    private static @Nullable TodoStatus statusOf(@Nullable Boolean completed) {
        if (completed == null) {
            return null;
        }
        return completed ? TodoStatus.COMPLETED : TodoStatus.PENDING;
    }

    private static ApplicationException notFound() {
        return ApplicationException.notFound("todo.not-found", "Todo not found.");
    }
}
