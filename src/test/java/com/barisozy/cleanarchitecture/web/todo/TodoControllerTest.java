package com.barisozy.cleanarchitecture.web.todo;

import static com.barisozy.cleanarchitecture.web.WebSliceTestConfiguration.jwtFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barisozy.cleanarchitecture.application.ApplicationException;
import com.barisozy.cleanarchitecture.application.PageResult;
import com.barisozy.cleanarchitecture.application.todo.TodoService;
import com.barisozy.cleanarchitecture.application.todo.TodoView;
import com.barisozy.cleanarchitecture.domain.todo.Todo;
import com.barisozy.cleanarchitecture.domain.user.User;
import com.barisozy.cleanarchitecture.fixtures.TodoTestData;
import com.barisozy.cleanarchitecture.fixtures.UserTestData;
import com.barisozy.cleanarchitecture.web.WebSliceTestConfiguration;
import com.barisozy.cleanarchitecture.web.error.GlobalExceptionHandler;
import com.barisozy.cleanarchitecture.web.error.ProblemDetailFactory;
import com.barisozy.cleanarchitecture.web.security.SecurityConfiguration;
import java.util.List;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Covers the status codes and the translation between HTTP and the todo service. */
@WebMvcTest(TodoController.class)
@Import({
    SecurityConfiguration.class,
    WebSliceTestConfiguration.class,
    ProblemDetailFactory.class,
    GlobalExceptionHandler.class
})
@ActiveProfiles("test")
class TodoControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private TodoService todoService;

    private User user;
    private Todo todo;
    private TodoView view;

    @BeforeEach
    void setUp() {
        user = UserTestData.withFixedId();
        todo = TodoTestData.pendingTodo(user.id());
        view = TodoTestData.view(todo);
    }

    @Test
    void exposesTheCrudEndpoints() {
        when(todoService.create(any(), any(), any(), any())).thenReturn(view);
        when(todoService.get(any(), any())).thenReturn(view);
        when(todoService.update(any(), any(), any(), any(), any())).thenReturn(view);
        Todo completed = todo.complete(UserTestData.NOW);
        when(todoService.complete(any(), any())).thenReturn(TodoTestData.view(completed));
        when(todoService.list(any(), any(), anyInt(), anyInt())).thenReturn(new PageResult<>(List.of(view), 0, 20, 1));

        MvcTestResult created = mvc.post()
                .uri("/api/todos")
                .with(jwtFor(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"Write plan","description":"Finish the architecture document","dueDate":"2026-11-10"}
                        """)
                .exchange();
        MvcTestResult found = mvc.get()
                .uri("/api/todos/{id}", todo.id().value())
                .with(jwtFor(user))
                .exchange();
        MvcTestResult updated = mvc.put()
                .uri("/api/todos/{id}", todo.id().value())
                .with(jwtFor(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"Write plan","description":"Finish the architecture document"}
                        """)
                .exchange();
        MvcTestResult completedResult = mvc.post()
                .uri("/api/todos/{id}/complete", todo.id().value())
                .with(jwtFor(user))
                .exchange();
        MvcTestResult deleted = mvc.delete()
                .uri("/api/todos/{id}", todo.id().value())
                .with(jwtFor(user))
                .exchange();
        MvcTestResult listed =
                mvc.get().uri("/api/todos?page=0&size=20").with(jwtFor(user)).exchange();

        assertThat(created).hasStatus(HttpStatus.CREATED);
        assertThat(created)
                .hasHeader(HttpHeaders.LOCATION, "/api/todos/" + todo.id().value());
        assertThat(created)
                .bodyJson()
                .extractingPath("$.id")
                .asString()
                .isEqualTo(todo.id().value().toString());
        assertThat(created)
                .bodyJson()
                .extractingPath("$.ownerId")
                .asString()
                .isEqualTo(user.id().value().toString());
        assertThat(created).bodyJson().extractingPath("$.completed").asBoolean().isFalse();
        assertThat(found).hasStatus(HttpStatus.OK);
        assertThat(found).bodyJson().extractingPath("$.description").asString().isEqualTo(TodoTestData.DESCRIPTION);
        assertThat(updated).hasStatus(HttpStatus.OK);
        assertThat(updated).bodyJson().extractingPath("$.title").asString().isEqualTo(TodoTestData.TITLE);
        assertThat(completedResult).hasStatus(HttpStatus.OK);
        assertThat(completedResult)
                .bodyJson()
                .extractingPath("$.completed")
                .asBoolean()
                .isTrue();
        assertThat(completedResult)
                .bodyJson()
                .extractingPath("$.completedAt")
                .asString()
                .isNotBlank();
        assertThat(deleted).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(listed).hasStatus(HttpStatus.OK);
        assertThat(listed)
                .bodyJson()
                .extractingPath("$.items[0].id")
                .asString()
                .isEqualTo(todo.id().value().toString());
        assertThat(listed)
                .bodyJson()
                .extractingPath("$.totalElements")
                .convertTo(InstanceOfAssertFactories.LONG)
                .isEqualTo(1L);
    }

    @Test
    void passesTheCompletionFilterToTheServiceAsABoolean() {
        when(todoService.list(any(), any(), anyInt(), anyInt())).thenReturn(new PageResult<>(List.of(), 0, 20, 0));

        mvc.get().uri("/api/todos?completed=true").with(jwtFor(user)).exchange();
        mvc.get().uri("/api/todos?completed=false").with(jwtFor(user)).exchange();

        ArgumentCaptor<Boolean> captor = ArgumentCaptor.forClass(Boolean.class);
        verify(todoService, times(2)).list(any(), captor.capture(), anyInt(), anyInt());
        assertThat(captor.getAllValues()).containsExactly(true, false);
    }

    @Test
    void mapsOwnershipAndCompletionFailures() {
        when(todoService.get(any(), any()))
                .thenThrow(ApplicationException.notFound("todo.not-found", "Todo not found."));
        when(todoService.complete(any(), any()))
                .thenThrow(ApplicationException.conflict("todo.already-completed", "Todo is already completed."));

        MvcTestResult missing = mvc.get()
                .uri("/api/todos/{id}", todo.id().value())
                .with(jwtFor(user))
                .exchange();
        MvcTestResult conflict = mvc.post()
                .uri("/api/todos/{id}/complete", todo.id().value())
                .with(jwtFor(user))
                .exchange();

        assertThat(missing)
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(missing).bodyJson().extractingPath("$.code").asString().isEqualTo("todo.not-found");
        assertThat(conflict)
                .hasStatus(HttpStatus.CONFLICT)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(conflict).bodyJson().extractingPath("$.code").asString().isEqualTo("todo.already-completed");
    }

    @Test
    void rejectsInvalidQueryParametersAndMissingTokens() {
        MvcTestResult invalidSize =
                mvc.get().uri("/api/todos?size=0").with(jwtFor(user)).exchange();
        MvcTestResult invalidFilter =
                mvc.get().uri("/api/todos?completed=maybe").with(jwtFor(user)).exchange();
        MvcTestResult anonymous = mvc.get().uri("/api/todos").exchange();

        assertThat(invalidSize)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(invalidSize).bodyJson().extractingPath("$.code").asString().isEqualTo("validation.failed");
        assertThat(invalidSize)
                .bodyJson()
                .extractingPath("$.fieldErrors")
                .asArray()
                .isNotEmpty();
        assertThat(invalidSize)
                .bodyJson()
                .extractingPath("$.fieldErrors[0].field")
                .asString()
                .isEqualTo("size");
        assertThat(invalidFilter).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(invalidFilter).bodyJson().extractingPath("$.code").asString().isEqualTo("validation.failed");
        assertThat(invalidFilter)
                .bodyJson()
                .extractingPath("$.fieldErrors")
                .asArray()
                .isNotEmpty();
        assertThat(invalidFilter)
                .bodyJson()
                .extractingPath("$.fieldErrors[0].field")
                .asString()
                .isEqualTo("completed");
        assertThat(anonymous)
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(anonymous).hasHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        assertThat(anonymous).bodyJson().extractingPath("$.code").asString().isEqualTo("security.unauthorized");
    }
}
