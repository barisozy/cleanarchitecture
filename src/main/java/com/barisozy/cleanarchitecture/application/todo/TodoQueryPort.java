package com.barisozy.cleanarchitecture.application.todo;

import com.barisozy.cleanarchitecture.application.PageResult;
import com.barisozy.cleanarchitecture.domain.todo.TodoStatus;
import com.barisozy.cleanarchitecture.domain.user.UserId;
import org.jspecify.annotations.Nullable;

/**
 * Read-model port of the todo feature.
 *
 * <p>A {@code null} owner means "every owner" and is only used for administrators. The port lives
 * in the application layer because it returns views, not aggregates. Implementations return the
 * newest todo first; the caller passes only the page index and size.
 */
public interface TodoQueryPort {

    PageResult<TodoView> findPage(@Nullable UserId ownerId, @Nullable TodoStatus status, int page, int size);
}
