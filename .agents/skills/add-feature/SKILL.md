---
name: add-feature
description: Scaffold a complete feature in this Java 25 / Spring Boot Clean Architecture template — a method on the feature service, Spring MVC controller endpoint, request/response DTOs, Flyway migration when the schema changes, and unit + web slice + integration tests. Use when the user asks to add a feature, use case, endpoint or service method to this template.
argument-hint: <feature description, e.g. "add a tag to a todo" or "list todos due this week">
---

# Add a Feature

A feature is one new public method on a service plus the HTTP endpoint that calls it. Work through the
layers in order and stop when the feature is complete; do not add interfaces, command records or
configuration classes that the feature does not need.

## Workflow

1. **Model the domain.** Add or extend the aggregate and its value objects; invariants throw
   `DomainException`. If persistence changes, add the repository method. See the `add-entity` skill for
   a new aggregate.
2. **Add the application method** on the feature service (`AuthService`, `TodoService`, or a new
   `@Service` class if the feature is unrelated). Take the caller and the plain values, return a view,
   throw `ApplicationException` for expected outcomes, read time from the injected `Clock`.
3. **Add the adapter** when a new port method is needed: `@Component` in `infrastructure`, a Flyway
   migration `V<n>__description.sql` when the schema changes.
4. **Add the endpoint** in `web/<feature>`: the controller method, request/response DTOs with a static
   `from(view)` factory, validation annotations, OpenAPI annotations and `@PreAuthorize`.
5. **Add the tests** — a service unit test with the fakes, a `MockMvcTester` slice test, and an
   integration test when the behaviour touches the database.
6. **Verify:** `./gradlew spotlessApply check` and, when Docker is available,
   `./gradlew integrationTest`.

## Worked example

A service method that reads one todo and the endpoint that publishes it:

```java
// application/todo/TodoService.java
@Transactional(readOnly = true)
public TodoView get(AuthenticatedUser caller, UUID todoId) {
    return TodoView.from(findReadable(caller, todoId));
}
```

```java
// web/todo/TodoController.java
@GetMapping("/{id}")
public ResponseEntity<TodoResponse> get(
        @AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
    return ResponseEntity.ok(TodoResponse.from(todoService.get(caller, id)));
}
```

```java
// web/todo/dto/TodoResponse.java
public record TodoResponse(UUID id, /* … */ boolean completed) {
    public static TodoResponse from(TodoView view) {
        return new TodoResponse(view.id(), /* … */ view.status() == TodoStatus.COMPLETED);
    }
}
```

## Conventions

- **One service per feature.** `@Service` in `application/<feature>`; one public method per use case.
  No `*UseCase` interface, no `command/` package, no `internal/` package.
- **Method signatures stay small.** Pass the values the method needs. Introduce a record only when the
  parameter list stops being readable at the call site.
- **The application layer is framework-aware but framework-clean.** Allowed: `@Service`,
  `@Transactional`, constructor injection, `PasswordEncoder`, `java.time.Clock`. Forbidden: JPA,
  Spring Data repositories, Spring Web, HTTP, Jackson, Bean Validation, SLF4J.
- **Ports only at real boundaries.** `UserRepository` and `TodoRepository` in `domain`,
  `TodoQueryPort` and `TokenIssuer` in `application`. Everything else is called directly.
- **Pagination is the application's own `PageResult<T>`.** Spring Data `Page`/`Pageable` stay inside
  the persistence adapter. `PageResponse.from(page, mapper)` does the web mapping.
- **Expected outcomes are thrown.** `ApplicationException` (via `validation`, `notFound`, `conflict`,
  `unauthorized`, `forbidden`) for a rejected but legitimate request; `DomainException` only for an
  invariant violation; anything else propagates as a `500`. Codes are stable strings such as
  `todo.not-found`; the application layer never mentions HTTP.
- **Aggregates are immutable.** Every mutation returns a new instance (`todo.complete(now)`), value
  objects validate themselves, and the domain never reads the clock.
- **Transactions live on the service.** `@Transactional` on a write that needs a unit of work,
  `@Transactional(readOnly = true)` on a read, nothing on a single insert or a CPU-bound path such as
  login. Adapters join the caller's transaction. A service with `@Transactional` must not be `final`.
- **Controllers hold no business logic.** They translate HTTP into a service call and back, and let
  `GlobalExceptionHandler` map the exceptions.
- **Validation happens at the edge.** Bean Validation on the request DTO and on query parameters
  (`@Min`/`@Max`, no class-level `@Validated`), plus the domain value objects.
- **Ownership is enforced in the service.** Somebody else's resource is `404`, never `403`. An
  administrator reads every todo but writes only its own.
- **Time is injected.** Application code reads `Clock`; domain methods take an explicit `Instant`.
- **Tests are mandatory and lean.** Fakes from `src/testFixtures`, `MockMvcTester` for the slice, one
  test per distinct behaviour.

## Naming

| Artifact | Pattern | Example |
|---|---|---|
| Service method | verb | `create`, `list`, `complete` |
| Service (new feature) | `{Feature}Service` | `TagService` |
| View | `{Entity}View` | `TodoView` |
| Request DTO | `{Verb}{Entity}Request` in `web/<feature>/dto/` | `TagTodoRequest` |
| Response DTO | `{Entity}Response` in `web/<feature>/dto/` | `TodoResponse` |
| Service test | `{Feature}ServiceTest` | `TodoServiceTest` |
| Web slice test | `{Feature}ControllerTest` | `TodoControllerTest` |
| Integration test | `{Feature}FlowIntegrationTest` | `TodoFlowIntegrationTest` |
| Test method | describes the behaviour | `hidesAMissingOrForeignTodoBehindNotFound` |
