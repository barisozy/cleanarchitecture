package com.barisozy.cleanarchitecture.application.todo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.barisozy.cleanarchitecture.application.ApplicationException;
import com.barisozy.cleanarchitecture.application.ErrorType;
import com.barisozy.cleanarchitecture.application.PageResult;
import com.barisozy.cleanarchitecture.application.security.AuthenticatedUser;
import com.barisozy.cleanarchitecture.domain.todo.Todo;
import com.barisozy.cleanarchitecture.domain.todo.TodoId;
import com.barisozy.cleanarchitecture.domain.todo.TodoStatus;
import com.barisozy.cleanarchitecture.domain.user.RoleName;
import com.barisozy.cleanarchitecture.domain.user.User;
import com.barisozy.cleanarchitecture.fixtures.FakeTodoQueryPort;
import com.barisozy.cleanarchitecture.fixtures.FakeTodoRepository;
import com.barisozy.cleanarchitecture.fixtures.FixedClock;
import com.barisozy.cleanarchitecture.fixtures.TodoTestData;
import com.barisozy.cleanarchitecture.fixtures.UserTestData;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TodoServiceTest {

    private User owner;
    private User other;
    private Todo ownTodo;
    private Todo otherTodo;
    private FakeTodoRepository todoRepository;
    private FakeTodoQueryPort todoQueryPort;
    private FixedClock clock;
    private TodoService service;
    private AuthenticatedUser ownerCaller;
    private AuthenticatedUser adminCaller;

    @BeforeEach
    void setUp() {
        owner = UserTestData.user();
        other = UserTestData.userWithEmail("other@example.com");
        ownTodo = TodoTestData.pendingTodo(owner.id());
        otherTodo = TodoTestData.pendingTodo(other.id());
        todoRepository = new FakeTodoRepository(ownTodo);
        todoQueryPort = new FakeTodoQueryPort(ownTodo, otherTodo);
        clock = new FixedClock(UserTestData.NOW);
        service = new TodoService(todoRepository, todoQueryPort, clock);
        ownerCaller = new AuthenticatedUser(owner.id(), RoleName.USER);
        adminCaller = new AuthenticatedUser(UserTestData.ADMIN_ID, RoleName.ADMIN);
    }

    @Test
    void createsTheTodoForTheAuthenticatedCaller() {
        TodoView view = service.create(ownerCaller, "  " + TodoTestData.TITLE + "  ", "   ", TodoTestData.DUE_DATE);

        assertThat(todoRepository.todos()).hasSize(2);
        assertThat(view.ownerId()).isEqualTo(owner.id().value());
        assertThat(todoRepository.get(new TodoId(view.id())).title().value()).isEqualTo(TodoTestData.TITLE);
        assertThat(view.title()).isEqualTo(TodoTestData.TITLE);
        assertThat(view.description()).isNull();
        assertThat(view.dueDate()).isEqualTo(TodoTestData.DUE_DATE);
        assertThat(view.status()).isEqualTo(TodoStatus.PENDING);
        assertThat(view.completedAt()).isNull();
        assertThat(view.createdAt()).isEqualTo(UserTestData.NOW);
        assertThat(view.updatedAt()).isEqualTo(UserTestData.NOW);
    }

    @Test
    void takesTheTimestampsFromTheClock() {
        clock.advance(Duration.ofHours(1));

        TodoView view =
                service.create(ownerCaller, TodoTestData.TITLE, TodoTestData.DESCRIPTION, TodoTestData.DUE_DATE);

        assertThat(view.createdAt()).isEqualTo(UserTestData.NOW.plusSeconds(3600));
        assertThat(view.updatedAt()).isEqualTo(UserTestData.NOW.plusSeconds(3600));
    }

    @Test
    void returnsTheTodoToItsOwnerAndToAnAdministrator() {
        TodoView view = service.get(ownerCaller, ownTodo.id().value());

        assertThat(view.id()).isEqualTo(ownTodo.id().value());
        assertThat(view.title()).isEqualTo(TodoTestData.TITLE);
        assertThat(view.description()).isEqualTo(TodoTestData.DESCRIPTION);
        assertThat(view.dueDate()).isEqualTo(TodoTestData.DUE_DATE);

        assertThat(service.get(adminCaller, ownTodo.id().value()).id())
                .isEqualTo(ownTodo.id().value());
    }

    @Test
    void hidesAMissingOrForeignTodoBehindNotFound() {
        AuthenticatedUser otherCaller = new AuthenticatedUser(other.id(), RoleName.USER);

        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> service.get(otherCaller, ownTodo.id().value()))
                .satisfies(TodoServiceTest::isNotFound);

        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> service.get(ownerCaller, UUID.randomUUID()))
                .satisfies(TodoServiceTest::isNotFound);
    }

    @Test
    void scopesTheListToTheCallerAndAppliesTheCompletionFilter() {
        PageResult<TodoView> own = service.list(ownerCaller, null, 0, 20);

        assertThat(own.items())
                .extracting(TodoView::id)
                .containsExactly(ownTodo.id().value());
        assertThat(own.totalElements()).isEqualTo(1);

        PageResult<TodoView> all = service.list(adminCaller, null, 0, 20);

        assertThat(all.items())
                .extracting(TodoView::id)
                .containsExactlyInAnyOrder(ownTodo.id().value(), otherTodo.id().value());
        assertThat(all.totalElements()).isEqualTo(2);

        Todo completed = TodoTestData.completedTodo(owner.id());
        FakeTodoQueryPort filterPort = new FakeTodoQueryPort(ownTodo, completed);
        TodoService filterService = new TodoService(new FakeTodoRepository(ownTodo, completed), filterPort, clock);

        PageResult<TodoView> completedOnly = filterService.list(ownerCaller, true, 0, 20);
        PageResult<TodoView> pendingOnly = filterService.list(ownerCaller, false, 0, 20);

        assertThat(completedOnly.items())
                .extracting(TodoView::id)
                .containsExactly(completed.id().value());
        assertThat(pendingOnly.items())
                .extracting(TodoView::id)
                .containsExactly(ownTodo.id().value());
    }

    @Test
    void passesThePaginationToTheReadModel() {
        service.list(ownerCaller, null, 1, 10);

        assertThat(todoQueryPort.lastPage()).isEqualTo(1);
        assertThat(todoQueryPort.lastSize()).isEqualTo(10);
    }

    @Test
    void updatesTheEditableFieldsAndKeepsTheCompletionState() {
        LocalDate newDueDate = LocalDate.of(2027, 1, 1);
        clock.advance(Duration.ofHours(1));

        TodoView view = service.update(ownerCaller, ownTodo.id().value(), "Renamed", "New description", newDueDate);

        assertThat(view.title()).isEqualTo("Renamed");
        assertThat(view.description()).isEqualTo("New description");
        assertThat(view.dueDate()).isEqualTo(newDueDate);
        assertThat(view.updatedAt()).isEqualTo(UserTestData.NOW.plusSeconds(3600));
        assertThat(view.createdAt()).isEqualTo(UserTestData.NOW);
        assertThat(todoRepository.get(ownTodo.id()).title().value()).isEqualTo("Renamed");

        // Updating a completed todo keeps it completed.
        Todo completed = TodoTestData.completedTodo(owner.id());
        TodoService withCompleted =
                new TodoService(new FakeTodoRepository(completed), new FakeTodoQueryPort(completed), clock);

        TodoView completedView =
                withCompleted.update(ownerCaller, completed.id().value(), "Renamed", null, null);

        assertThat(completedView.status()).isEqualTo(TodoStatus.COMPLETED);
        assertThat(completedView.completedAt()).isEqualTo(UserTestData.NOW);
    }

    @Test
    void hidesATodoTheCallerMayNotWrite() {
        AuthenticatedUser otherCaller = new AuthenticatedUser(other.id(), RoleName.USER);

        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> service.update(otherCaller, ownTodo.id().value(), "Hijacked", null, null))
                .satisfies(TodoServiceTest::isNotFound);
        assertThat(todoRepository.get(ownTodo.id()).title().value()).isEqualTo(TodoTestData.TITLE);

        // An administrator reads every todo but writes only its own.
        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> service.update(adminCaller, ownTodo.id().value(), "Admin edit", null, null))
                .satisfies(TodoServiceTest::isNotFound);
        assertThat(todoRepository.get(ownTodo.id()).title().value()).isEqualTo(TodoTestData.TITLE);

        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> service.update(ownerCaller, UUID.randomUUID(), "Renamed", null, null))
                .satisfies(TodoServiceTest::isNotFound);
    }

    @Test
    void completesAPendingTodoOnlyOnce() {
        TodoView view = service.complete(ownerCaller, ownTodo.id().value());

        assertThat(view.status()).isEqualTo(TodoStatus.COMPLETED);
        assertThat(view.completedAt()).isEqualTo(UserTestData.NOW);
        assertThat(todoRepository.get(ownTodo.id()).status()).isEqualTo(TodoStatus.COMPLETED);

        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> service.complete(ownerCaller, ownTodo.id().value()))
                .satisfies(error -> {
                    assertThat(error.code()).isEqualTo("todo.already-completed");
                    assertThat(error.type()).isEqualTo(ErrorType.CONFLICT);
                });
    }

    @Test
    void hidesAMissingOrForeignTodoWhenCompleting() {
        AuthenticatedUser otherCaller = new AuthenticatedUser(other.id(), RoleName.USER);

        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> service.complete(otherCaller, ownTodo.id().value()))
                .satisfies(TodoServiceTest::isNotFound);
        assertThat(todoRepository.get(ownTodo.id()).status()).isEqualTo(TodoStatus.PENDING);

        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> service.complete(ownerCaller, UUID.randomUUID()))
                .satisfies(TodoServiceTest::isNotFound);
    }

    @Test
    void removesOnlyTheTodoOfTheOwner() {
        assertThatNoException()
                .isThrownBy(() -> service.delete(ownerCaller, ownTodo.id().value()));

        assertThat(todoRepository.todos()).isEmpty();

        // Another user's todo is hidden behind a not found and is kept.
        TodoService otherService =
                new TodoService(new FakeTodoRepository(ownTodo), new FakeTodoQueryPort(ownTodo), clock);
        AuthenticatedUser otherCaller = new AuthenticatedUser(other.id(), RoleName.USER);

        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> otherService.delete(otherCaller, ownTodo.id().value()))
                .satisfies(TodoServiceTest::isNotFound);

        // An unknown id is a not found as well.
        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> otherService.delete(ownerCaller, UUID.randomUUID()))
                .satisfies(TodoServiceTest::isNotFound);
    }

    private static void isNotFound(ApplicationException error) {
        assertThat(error.code()).isEqualTo("todo.not-found");
        assertThat(error.type()).isEqualTo(ErrorType.NOT_FOUND);
    }
}
