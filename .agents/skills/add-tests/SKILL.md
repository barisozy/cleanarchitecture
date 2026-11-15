---
name: add-tests
description: Backfill missing tests for existing service methods in this Clean Architecture template — service unit tests with the shared fakes, web slice tests with MockMvcTester and HTTP integration tests. Use when the user asks to add, improve or backfill test coverage.
argument-hint: <service method or feature to cover, e.g. "TodoService.complete" or "the Todo feature">
---

# Add Tests for an Existing Feature

Backfill the three test types this template expects. Read the service method and its controller first,
then mirror the closest existing test class.

## Workflow

1. **Locate the slice.** Find the service, the controller and the request DTO. List every distinct
   outcome: the happy path and each `ApplicationException` or `DomainException` branch.
2. **Check what exists** in `src/test/java/.../application/`, `src/test/java/.../web/` and
   `src/integrationTest/` — extend the existing class, do not duplicate it.
3. **Write the service unit test** — one test per failure branch plus the happy path, asserting the
   error code, the error type and the state left in the fake.
4. **Write the web slice test** — the success status, the `Location` header, the `ProblemDetail`
   fields, the validation failure and the unauthenticated request.
5. **Write the integration test** — the happy path over the real stack with `MockMvcTester` and the
   state asserted through a follow-up request, plus the important failures (404 for a foreign
   resource, 409 for a conflict, 401 without a token).
6. **Run** `./gradlew spotlessApply check` and, when Docker is available, `./gradlew integrationTest`.

## Conventions

- **Frameworks:** JUnit 6, AssertJ, Mockito. `@WebMvcTest` with `MockMvcTester` for the web layer,
  Testcontainers for the integration layer.
- **Fakes:** the hand-written fakes in `src/testFixtures/java/.../fixtures/` (`FakeUserRepository`,
  `FakeTodoRepository`, `FakeTodoQueryPort`, `FakeTokenIssuer`). Extend them when a new collaborator is
  needed — do not mock the domain repositories. The fakes are behaviourless: they store and read, they
  apply no business rule and read no clock.
- **Fixed time:** `UserTestData.CLOCK` and `UserTestData.NOW`. Never assert on `Instant.now()`.
- **Configuration:** a new `@ConfigurationProperties` group also gets an `ApplicationContextRunner`
  test with an invalid value; `ValidConfigurationTest` must keep binding the shipped YAML.
- **Architecture rules:** keep `ArchitectureTest` compact. A new boundary gets one rule; the suite does
  not need a violation fixture per rule.
- **Test data:** `UserTestData`, `TodoTestData`, `AuthTestData`.
- **Naming:** classes `{Feature}ServiceTest`, `{Feature}ControllerTest`; methods describe the
  behaviour, e.g. `completesAPendingTodoOnlyOnce`,
  `hidesAMissingOrForeignTodoBehindNotFound`.
- **Assertions:** compare the exact error code and type, never the message. Assert the persisted state
  through the fake repository or through a follow-up HTTP request.
- **Expected failures:** `assertThatExceptionOfType(ApplicationException.class).isThrownBy(...)` in the
  unit test, `thenThrow(...)` on the mocked service in the web slice test.
- **Web slice tests:** the JWT principal comes from `jwtFor(UserTestData.user())`
  (`SecurityMockMvcRequestPostProcessors.jwt()`); the service is a `@MockitoBean`.
- **Security in the slice tests:** CSRF is disabled, because the API writes no cookie.
- **Lean suite:** one test per distinct behaviour. Several assertions in one test are welcome; one test
  per assertion is not. Merge near-identical cases with a `@ParameterizedTest` where it reads better.

## Unit-test shape

```java
@Test
void hidesAMissingOrForeignTodoBehindNotFound() {
    AuthenticatedUser otherCaller = new AuthenticatedUser(other.id(), RoleName.USER);

    assertThatExceptionOfType(ApplicationException.class)
            .isThrownBy(() -> service.get(otherCaller, ownTodo.id().value()))
            .satisfies(error -> {
                assertThat(error.code()).isEqualTo("todo.not-found");
                assertThat(error.type()).isEqualTo(ErrorType.NOT_FOUND);
            });
}
```
