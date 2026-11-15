package com.barisozy.cleanarchitecture.fixtures;

import com.barisozy.cleanarchitecture.domain.todo.Todo;
import com.barisozy.cleanarchitecture.domain.todo.TodoId;
import com.barisozy.cleanarchitecture.domain.todo.TodoRepository;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * In-memory todo repository for the application unit tests.
 *
 * <p>The fake only stores, reads, replaces and removes. It applies no business rule and reads no
 * clock: the rules are verified in the domain tests.
 */
public final class FakeTodoRepository implements TodoRepository {

    private final Map<TodoId, Todo> todos = new LinkedHashMap<>();

    public FakeTodoRepository(Todo... initialTodos) {
        for (Todo todo : initialTodos) {
            todos.put(todo.id(), todo);
        }
    }

    @Override
    public Optional<Todo> findById(TodoId id) {
        return Optional.ofNullable(todos.get(id));
    }

    @Override
    public Todo insert(Todo todo) {
        todos.put(todo.id(), todo);
        return todo;
    }

    @Override
    public Todo update(Todo todo) {
        todos.put(todo.id(), todo);
        return todo;
    }

    @Override
    public void delete(Todo todo) {
        todos.remove(todo.id());
    }

    /** The stored todos; the insertion order is preserved so that assertions stay deterministic. */
    public Map<TodoId, Todo> todos() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(todos));
    }

    public Todo get(TodoId id) {
        return Objects.requireNonNull(todos.get(id), "todo");
    }
}
