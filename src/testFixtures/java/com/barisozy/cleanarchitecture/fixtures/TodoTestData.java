package com.barisozy.cleanarchitecture.fixtures;

import com.barisozy.cleanarchitecture.application.todo.TodoView;
import com.barisozy.cleanarchitecture.domain.todo.Todo;
import com.barisozy.cleanarchitecture.domain.todo.TodoDescription;
import com.barisozy.cleanarchitecture.domain.todo.TodoTitle;
import com.barisozy.cleanarchitecture.domain.user.UserId;
import java.time.LocalDate;

/** Deterministic todo fixtures shared by the unit, web and integration tests. */
public final class TodoTestData {

    public static final String TITLE = "Write plan";
    public static final String DESCRIPTION = "Finish the architecture document";
    public static final LocalDate DUE_DATE = LocalDate.of(2026, 11, 10);

    private TodoTestData() {}

    /** A pending todo owned by the given user. */
    public static Todo pendingTodo(UserId ownerId) {
        return Todo.create(
                ownerId, TodoTitle.of(TITLE), TodoDescription.ofNullable(DESCRIPTION), DUE_DATE, UserTestData.NOW);
    }

    /** A completed todo owned by the given user. */
    public static Todo completedTodo(UserId ownerId) {
        return pendingTodo(ownerId).complete(UserTestData.NOW);
    }

    public static TodoView view(Todo todo) {
        return TodoView.from(todo);
    }
}
