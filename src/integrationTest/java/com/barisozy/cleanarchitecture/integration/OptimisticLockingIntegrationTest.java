package com.barisozy.cleanarchitecture.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.barisozy.cleanarchitecture.application.ApplicationException;
import com.barisozy.cleanarchitecture.domain.todo.Todo;
import com.barisozy.cleanarchitecture.domain.todo.TodoId;
import com.barisozy.cleanarchitecture.domain.todo.TodoRepository;
import com.barisozy.cleanarchitecture.domain.todo.TodoTitle;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Covers the optimistic lock of the todo aggregate.
 *
 * <p>The lock is the {@code version} column of the row. A write that is based on a revision the row
 * has moved past affects no row and has to be reported as a conflict instead of overwriting a
 * concurrent change.
 *
 * <p>The race is manufactured deterministically: the write of the request is paused between the
 * read and the flush, and another writer commits in between. Without that pause the request would
 * read the row and write it in one go, and the conflict would depend on the timing of two
 * concurrent requests.
 */
class OptimisticLockingIntegrationTest extends AbstractIntegrationTest {

    @MockitoSpyBean
    private TodoRepository todoRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ExecutorService writer = Executors.newSingleThreadExecutor();

    @AfterEach
    void stopWriter() {
        writer.shutdownNow();
    }

    @Test
    void reportsAConflictWhenAnotherWriterChangedTheTodoInBetween() {
        String token = register(uniqueEmail("conflict"));
        String todoId = jsonString(createTodo(token, "Original title"), "$.id");

        // The spy lets another writer commit between the read and the write of the request.
        doAnswer(invocation -> {
                    writeConcurrently(todoId, "Written by another request");
                    return invocation.callRealMethod();
                })
                .when(todoRepository)
                .update(any());

        MvcTestResult result = mvc.put()
                .uri("/api/todos/{id}", todoId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"Our title"}
                        """)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo("concurrency.conflict");
    }

    /**
     * The same race, one layer below: the aggregate is read in a transaction that stays open while
     * a second transaction commits, so the write is based on a revision the row has moved past.
     */
    @Test
    void rejectsAStaleWriteOfAnAggregateThatWasReadBeforeAnotherWriterCommitted() {
        String token = register(uniqueEmail("stale"));
        String todoId = jsonString(createTodo(token, "Original title"), "$.id");
        TodoId id = new TodoId(UUID.fromString(todoId));

        TransactionTemplate reader = new TransactionTemplate(transactionManager);
        TransactionTemplate concurrent = new TransactionTemplate(transactionManager);
        concurrent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> reader.executeWithoutResult(status -> {
                    Todo stale = todoRepository.findById(id).orElseThrow();
                    concurrent.executeWithoutResult(inner -> jdbcTemplate.update(
                            "UPDATE todos SET title = ?, version = version + 1 WHERE id = ?",
                            "Written by another transaction",
                            UUID.fromString(todoId)));
                    todoRepository.update(stale.update(TodoTitle.of("Loser"), null, null, Instant.now()));
                }))
                .isInstanceOfSatisfying(
                        ApplicationException.class,
                        exception -> assertThat(exception.code()).isEqualTo("concurrency.conflict"));

        assertThat(storedTitle(todoId)).isEqualTo("Written by another transaction");
    }

    private MvcTestResult createTodo(String token, String title) {
        MvcTestResult result = mvc.post()
                .uri("/api/todos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"%s\"}".formatted(title))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return result;
    }

    /** Commits a write of another connection, so that the request reads a stale revision. */
    private void writeConcurrently(String todoId, String title) {
        Future<?> future = writer.submit(() -> jdbcTemplate.update(
                "UPDATE todos SET title = ?, version = version + 1 WHERE id = ?", title, UUID.fromString(todoId)));
        try {
            future.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The concurrent write was interrupted.", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("The concurrent write failed.", exception);
        }
    }

    private String storedTitle(String todoId) {
        return jdbcTemplate.queryForObject(
                "SELECT title FROM todos WHERE id = ?", String.class, UUID.fromString(todoId));
    }
}
