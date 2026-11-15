package com.barisozy.cleanarchitecture.fixtures;

import com.barisozy.cleanarchitecture.application.PageResult;
import com.barisozy.cleanarchitecture.application.todo.TodoQueryPort;
import com.barisozy.cleanarchitecture.application.todo.TodoView;
import com.barisozy.cleanarchitecture.domain.todo.Todo;
import com.barisozy.cleanarchitecture.domain.todo.TodoStatus;
import com.barisozy.cleanarchitecture.domain.user.UserId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * In-memory read-model port for the application unit tests.
 *
 * <p>The fake filters and pages the todos it was given, newest first. It records the page size it
 * received, so that a test can verify what the service passes to the read model.
 */
public final class FakeTodoQueryPort implements TodoQueryPort {

    private final Supplier<List<Todo>> source;

    private int lastPage = -1;
    private int lastSize = -1;

    public FakeTodoQueryPort(Todo... initialTodos) {
        List<Todo> initial = List.of(initialTodos);
        this.source = () -> initial;
    }

    private FakeTodoQueryPort(Supplier<List<Todo>> source) {
        this.source = source;
    }

    /**
     * Reads through to {@code repository} on every call, so a write-then-read flow observes the
     * same state as the command side.
     */
    public static FakeTodoQueryPort backedBy(FakeTodoRepository repository) {
        return new FakeTodoQueryPort(() -> List.copyOf(repository.todos().values()));
    }

    @Override
    public PageResult<TodoView> findPage(@Nullable UserId ownerId, @Nullable TodoStatus status, int page, int size) {
        lastPage = page;
        lastSize = size;
        List<Todo> filtered = new ArrayList<>(source.get().stream()
                .filter(todo -> ownerId == null || todo.isOwnedBy(ownerId))
                .filter(todo -> status == null || todo.status() == status)
                .toList());
        filtered.sort(Comparator.comparing(Todo::createdAt)
                .reversed()
                .thenComparing(todo -> todo.title().value()));

        long total = filtered.size();
        int from = (int) Math.min((long) page * size, total);
        int to = Math.min(from + size, (int) total);
        List<TodoView> items =
                filtered.subList(from, to).stream().map(TodoView::from).toList();
        return new PageResult<>(items, page, size, total);
    }

    /** The page index of the last call. */
    public int lastPage() {
        return lastPage;
    }

    /** The page size of the last call. */
    public int lastSize() {
        return lastSize;
    }
}
