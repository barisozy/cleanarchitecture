---
name: ca-review
description: Review pending changes against this Clean Architecture template's conventions — layer boundaries, exception-based error handling, component wiring, configuration validation, security and test coverage. Use when the user asks to review changes, check conventions or audit a feature before committing.
argument-hint: [optional: specific files or feature to review; defaults to the working-tree diff]
---

# Clean Architecture Convention Review

Review the given scope (default: the working-tree diff plus the untracked files) against this
template's conventions. Report findings with `file:line` references, ordered by severity. Do not fix
anything unless asked.

## Checklist

### Layer boundaries (violations are blockers)

- `domain` depends only on the JDK and JSpecify — no Spring, JPA, Jackson, HTTP or logging imports,
  and no direct clock reads.
- `application` depends only on `domain` and the allowed framework APIs — `@Service`,
  `@Transactional`, constructor injection, `PasswordEncoder` and `java.time.Clock`. No JPA, Spring
  Data repositories, Spring Web, HTTP, Jackson, Bean Validation or logging imports; no reference to
  `infrastructure` or `web`.
- Pagination is the application's `PageResult<T>`; Spring Data `Page`/`Pageable` appear only inside
  `infrastructure.persistence`.
- Access tokens are issued through the `TokenIssuer` port; `JwtTokenIssuer` and `JwtEncoder` stay in
  `infrastructure.security`.
- `web` never imports `infrastructure`; `infrastructure` never imports `web`.
- `web` may depend only on the allowed domain value types (`TodoId`, `UserId`, `TodoStatus`,
  `RoleName`), never on aggregates, repositories or JPA entities.
- JPA entities live only in `infrastructure.persistence` and never leak into the application or web
  layer.
- `@Transactional` is only used in the `application` layer, on a feature service. A single-insert or
  CPU-bound method may rely on the repository transaction instead. There is no `UnitOfWork` port.

### Service structure

- One `@Service` class per feature (`AuthService`, `TodoService`), in `application/<feature>/`, whose
  public methods are the use cases. No `*UseCase` interface, no `internal/` package, no command record
  unless a single parameter type genuinely improves the call site.
- The service depends on repository *interfaces* and ports only, and is **not** `final` when it
  carries `@Transactional`, because the annotation is applied through a CGLIB proxy.
- The aggregate is immutable: every mutation returns a new instance; the invariants live in the
  compact constructor and throw a `DomainException` with a stable code.
- Value objects validate themselves and throw `DomainException` with a stable code.
- The read-model port that returns application views lives in `application/<feature>/` and is
  implemented by the same persistence adapter as the repository when both are backed by one table.
- `TodoStatus` is `PENDING` or `COMPLETED` and is bound to `completedAt` by one invariant:
  `COMPLETED` if and only if `completedAt != null`.

### Error handling

- Expected outcomes throw an `ApplicationException` created through its static factories
  (`validation`, `notFound`, `conflict`, `unauthorized`, `forbidden`); the category always matches
  the semantics of the failure.
- The error codes are stable lower-case strings (`todo.not-found`, `auth.email-already-registered`)
  and the `ErrorType` matches the semantics (`NOT_FOUND`, `CONFLICT`, `UNAUTHORIZED`, `FORBIDDEN`,
  `VALIDATION`).
- The application layer never mentions HTTP status codes; the web layer maps the type in the global
  `GlobalExceptionHandler`.
- Invariant violations throw a `DomainException` with a stable code, mapped to `422`; anything else
  propagates as an unexpected failure.
- No swallowed exceptions and no empty catch blocks.

### Wiring and web layer

- The feature service is `@Service`-annotated in `application/<feature>/` and found by the component
  scan. A configuration class exists only for an infrastructure object (for example
  `PasswordConfiguration`, `SecurityTokenConfiguration` or `ClockConfiguration`); it never creates a
  service. Adapters such as `JwtTokenIssuer` are `@Component`s.
- The application layer does not read the operating-system clock; services inject `java.time.Clock`
  and pass an `Instant` into the domain.
- The controller depends on the service class.
- The controller contains no business logic: request DTO and `AuthenticatedUser` → service method →
  response DTO.
- Request DTOs carry Jakarta Bean Validation and `@Schema`; the response DTOs are built from the
  application view.
- `@PreAuthorize("isAuthenticated()")` on every protected endpoint. There is no permission claim and
  no RBAC table: the roles are `USER` and `ADMIN`.
- Ownership is enforced in the application layer, and a resource the caller may not see is reported
  as `404`, never as `403`.
- OpenAPI metadata is present. Do not add `@SecurityRequirement` manually; the OpenAPI customizer
  adds `bearerAuth` only to operations with `@PreAuthorize`.
- The path carries no version segment and there is no `API-Version` header.

### Configuration and API contract

- Every `@ConfigurationProperties` record is `@Validated` and constrains the values it needs
  (`@NotBlank`, `@NotEmpty`, `@Min`/`@Max`, `@Valid` on nested records). A compact constructor may
  enforce a cross-field or encoded-value rule that Bean Validation cannot express, such as a Base64
  secret's decoded length; ad-hoc validation that an annotation already covers is a finding.
- A new property has an invalid-value test (`ApplicationContextRunner`), and the shipped
  `application.yaml` is covered by `ValidConfigurationTest`.
- The request body uses `@Valid`; query and path parameters carry their constraints directly, with no
  class-level `@Validated`. Both produce the same `400 validation.failed` envelope.

### Security and operations

- Passwords are hashed with BCrypt; hashes are never logged or returned. The 72-byte limit of the
  algorithm is enforced by `RawPassword` (`forRegistration` also requires 12 characters, `forLogin`
  only the byte limit); `AuthService.login` maps both a malformed address and a wrong password onto
  the same `auth.invalid-credentials`.
- The JWT is signed with HS256 and a Base64 secret of at least 32 bytes. The ephemeral key is bound to
  the `local` profile in `SecurityTokenConfiguration`; every other profile requires the configured
  secret. The token travels in the `Authorization` header; there is no cookie, no refresh token, no
  logout endpoint and therefore no CSRF surface. Flag any code that reintroduces them.
- There is no application-level rate limiting — that is the job of the edge/WAF. A change that adds
  a per-instance counter without a decision is a finding, as is a claim in the documentation that
  the application is protected against brute force.
- The correlation id is validated before it enters the MDC and is echoed in the response header. No
  user id or other PII is put in the MDC.
- No business identifier in a log message or in a URL without encoding.
- No token, cookie, password or `Authorization` header is logged.
- No `System.out`, no `printStackTrace`, no empty catch block.

### Tests

- Every service method has a unit test with the fakes from `src/testFixtures`, covering every
  `ApplicationException` branch and the happy path.
- Every controller has a web slice test with `MockMvcTester` covering the success status, the
  `ProblemDetail` fields, the validation failure and the authorization failures.
- Behaviour that touches the database has an integration test.
- New configuration properties have a validation test.
- Keep `ArchitectureTest` compact: one rule per boundary a reader can see in the package structure.
- The suite stays lean: one test per distinct behaviour, not one test per assertion.
- `./gradlew spotlessApply check` passes; `./gradlew integrationTest` passes when Docker is
  available. The single coverage gate (`domain` + `application`, 70 % lines) must be met.

## Output format

Group the findings into **Blockers** (layer violations, missing authorization, a missing or
mismatched `ApplicationException`), **Convention violations** (naming, structure, error codes,
missing wiring) and **Test gaps**. For each finding give `file:line`, what is wrong and the
one-line fix. Close with a verdict: ready to commit, or what has to change first. If everything
passes, say so and run `./gradlew spotlessApply check` to confirm.
