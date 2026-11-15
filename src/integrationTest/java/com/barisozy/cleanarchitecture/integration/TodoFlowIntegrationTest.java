package com.barisozy.cleanarchitecture.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Covers the todo feature over HTTP: the lifecycle of a todo, the ownership rules and the page
 * contract of the list.
 */
class TodoFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    void supportsTheFullLifecycleOfATodo() {
        String token = register(uniqueEmail("lifecycle"));

        MvcTestResult created = createTodo(token, "Write the plan");
        assertThat(created).hasStatus(HttpStatus.CREATED);
        assertThat(created.getResponse().getHeader(HttpHeaders.LOCATION)).isNotBlank();
        String todoId = jsonString(created, "$.id");
        assertThat(created).bodyJson().extractingPath("$.title").asString().isEqualTo("Write the plan");
        assertThat(created).bodyJson().extractingPath("$.completed").asBoolean().isFalse();
        assertThat(created).bodyJson().extractingPath("$.completedAt").isNull();

        MvcTestResult updated = mvc.put()
                .uri("/api/todos/{id}", todoId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"Write the final plan","description":"With the architecture"}
                        """)
                .exchange();
        assertThat(updated).hasStatusOk();
        assertThat(updated).bodyJson().extractingPath("$.title").asString().isEqualTo("Write the final plan");

        MvcTestResult completed = mvc.post()
                .uri("/api/todos/{id}/complete", todoId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange();
        assertThat(completed).hasStatusOk();
        assertThat(completed)
                .bodyJson()
                .extractingPath("$.completed")
                .asBoolean()
                .isTrue();
        assertThat(completed)
                .bodyJson()
                .extractingPath("$.completedAt")
                .asString()
                .isNotBlank();

        assertThat(mvc.delete().uri("/api/todos/{id}", todoId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/todos/{id}", todoId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.code")
                .asString()
                .isEqualTo("todo.not-found");
    }

    /** A resource the caller may not see is reported as 404, so that its existence stays hidden. */
    @Test
    void hidesTheTodosOfAnotherUser() {
        String ownerToken = register(uniqueEmail("owner"));
        String otherToken = register(uniqueEmail("intruder"));
        String todoId = jsonString(createTodo(ownerToken, "Private todo"), "$.id");

        assertNotFound(
                mvc.get().uri("/api/todos/{id}", todoId).header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken));
        assertNotFound(mvc.put()
                .uri("/api/todos/{id}", todoId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"Hijacked"}
                        """));
        assertNotFound(mvc.post()
                .uri("/api/todos/{id}/complete", todoId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken));
        assertNotFound(
                mvc.delete().uri("/api/todos/{id}", todoId).header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken));

        assertThat(mvc.get().uri("/api/todos/{id}", todoId).header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .hasStatusOk();
    }

    /** An administrator reads every todo but writes only its own. */
    @Test
    void letsAnAdministratorReadEveryTodoButWriteOnlyItsOwn() {
        String userToken = register(uniqueEmail("regular"));
        String todoId = jsonString(createTodo(userToken, "Owned by a regular user"), "$.id");
        String adminToken = createAdmin("admin");

        MvcTestResult list = mvc.get()
                .uri("/api/todos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .exchange();
        assertThat(list).hasStatusOk();
        assertThat(list)
                .bodyJson()
                .extractingPath("$.totalElements")
                .convertTo(InstanceOfAssertFactories.LONG)
                .isEqualTo(1L);

        assertThat(mvc.get().uri("/api/todos/{id}", todoId).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .hasStatusOk();

        assertNotFound(mvc.put()
                .uri("/api/todos/{id}", todoId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"Written by the admin"}
                        """));
    }

    @Test
    void rejectsTheCompletionOfAnAlreadyCompletedTodo() {
        String token = register(uniqueEmail("complete-twice"));
        String todoId = jsonString(createTodo(token, "Complete me"), "$.id");

        assertThat(mvc.post()
                        .uri("/api/todos/{id}/complete", todoId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .hasStatusOk();

        assertThat(mvc.post()
                        .uri("/api/todos/{id}/complete", todoId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.code")
                .asString()
                .isEqualTo("todo.already-completed");
    }

    /** The page contract and the completion filter of the list. */
    @Test
    void listsTheTodosAsAPageAndHonoursTheCompletedFilter() {
        String token = register(uniqueEmail("page"));
        String pendingId = jsonString(createTodo(token, "Still open"), "$.id");
        String completedId = jsonString(createTodo(token, "Already done"), "$.id");
        assertThat(mvc.post()
                        .uri("/api/todos/{id}/complete", completedId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .hasStatusOk();

        MvcTestResult firstPage = mvc.get()
                .uri("/api/todos")
                .queryParam("page", "0")
                .queryParam("size", "1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange();
        assertThat(firstPage).hasStatusOk();
        assertThat(firstPage).bodyJson().extractingPath("$.items").asArray().hasSize(1);
        assertThat(firstPage)
                .bodyJson()
                .extractingPath("$.page")
                .convertTo(InstanceOfAssertFactories.INTEGER)
                .isEqualTo(0);
        assertThat(firstPage)
                .bodyJson()
                .extractingPath("$.size")
                .convertTo(InstanceOfAssertFactories.INTEGER)
                .isEqualTo(1);
        assertThat(firstPage)
                .bodyJson()
                .extractingPath("$.totalElements")
                .convertTo(InstanceOfAssertFactories.LONG)
                .isEqualTo(2L);
        assertThat(firstPage)
                .bodyJson()
                .extractingPath("$.totalPages")
                .convertTo(InstanceOfAssertFactories.INTEGER)
                .isEqualTo(2);
        assertThat(firstPage).bodyJson().extractingPath("$.hasNext").asBoolean().isTrue();

        MvcTestResult pending = mvc.get()
                .uri("/api/todos")
                .queryParam("completed", "false")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange();
        MvcTestResult completed = mvc.get()
                .uri("/api/todos")
                .queryParam("completed", "true")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange();

        assertThat(pending)
                .bodyJson()
                .extractingPath("$.items[0].id")
                .asString()
                .isEqualTo(pendingId);
        assertThat(completed)
                .bodyJson()
                .extractingPath("$.items[0].id")
                .asString()
                .isEqualTo(completedId);
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

    private static void assertNotFound(MockMvcTester.MockMvcRequestBuilder request) {
        assertThat(request.exchange())
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.code")
                .asString()
                .isEqualTo("todo.not-found");
    }
}
