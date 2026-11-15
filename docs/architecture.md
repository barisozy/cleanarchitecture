# Architecture

This document describes how the template is built and why. The [README](../README.md) is the quick
start; this is the design reference.

## 1. Shape of the system

One Gradle project, one Spring Boot application, one PostgreSQL database. The layers are
**packages**, not modules or services. There is no Spring Modulith, no event bus and no second
deployable. The feature packages (`auth`, `todo`) appear in several layers; they are a naming
convention for humans, and the compiler only enforces the layer boundary.

```text
src/main/java/com/barisozy/cleanarchitecture
├── domain           aggregates, value objects, invariants, repository interfaces
├── application      feature services, views, ports
├── infrastructure   JPA adapters, security adapters, configuration
└── web              controllers, DTOs, security rules, error handling, OpenAPI
```

The scope is deliberately small: two aggregates (`User`, `Todo`), nine endpoints, four ports
(`UserRepository`, `TodoRepository`, `TodoQueryPort`, `TokenIssuer`) and one Flyway migration. No
background jobs, no cache, no message broker.

## 2. Layers and the dependency rule

```text
web ──────────▶ application ──────────▶ domain
                     ▲                    │
                     │                    │
              infrastructure ─────────────┘
          (implements the application and domain ports)
```

| Layer | May depend on | Must not contain |
|---|---|---|
| `domain` | JDK, JSpecify | Spring, JPA, Jackson, HTTP, direct clock reads |
| `application` | `domain`, `@Service`, `@Transactional`, `PasswordEncoder`, `Clock` | JPA, Spring Data repositories, HTTP, Jackson, Bean Validation, SLF4J, `infrastructure`, `web` |
| `infrastructure` | everything below it | Business rules |
| `web` | `application`, allowed domain value types, Spring Web/Security, Bean Validation, OpenAPI | `infrastructure` imports, JPA entities, aggregates, repositories |

The rules are enforced by
`src/test/java/com/barisozy/cleanarchitecture/architecture/ArchitectureTest.java`; a violation fails
`./gradlew test`. The rules are few and direct:

1. `domain` imports no framework and never reads the operating-system clock.
2. `application` imports no JPA, no Spring Data repository, no Spring Web, no Jackson, no Bean
   Validation and no SLF4J. It may use Spring for wiring, the transaction boundary and the password
   encoder.
3. `domain` never depends on an outer layer; `application` never depends on `infrastructure` or
   `web`; `web` never imports `infrastructure`, a JPA entity, a repository or an aggregate. It may
   reference the value types `UserId`, `TodoId`, `TodoStatus` and `RoleName`, plus `DomainException`
   for the error mapping.
4. JPA entities exist only under `infrastructure.persistence`; `*Repository` interfaces live in
   `domain`; read-model `*QueryPort` interfaces live in `application`.
5. `@Transactional` appears only in `application`.
6. Each feature exposes one `@Service` class — `AuthService`, `TodoService` — whose public methods are
   the use cases. There is no interface per use case and no `internal` package.
7. Time comes from an injected `java.time.Clock` in the application layer and from an explicit
   `Instant` in the domain.
8. The layer packages form an acyclic graph; Lombok and MapStruct are absent; `Optional` is never a
   field or a getter type.

The application layer is framework-aware on purpose: `@Service`, `@Transactional`, `PasswordEncoder`
and `Clock` are library APIs, not infrastructure leaks. Everything that changes per deployment and
has a real boundary is expressed as a port. Persistence is behind `UserRepository`, `TodoRepository`
and `TodoQueryPort`; token signing is behind `TokenIssuer`. Wrapping `@Transactional` or `Clock`
would add indirection without removing a dependency.

## 3. Request flow

```text
HTTP request
  │
  ├─ CorrelationIdFilter        assign/propagate X-Correlation-Id, put it in the MDC
  ├─ Spring Security            CORS, stateless JWT decoding, @PreAuthorize
  │
  ├─ Controller                 HTTP ⇄ service call, @Valid, DTO mapping
  ├─ Service (@Service)         use cases, ownership rules, ApplicationException
  ├─ Aggregate                  invariants; a violation throws DomainException
  ├─ Repository / QueryPort     declared in domain / application
  └─ Adapter                    JPA, JWT
```

The controller knows nothing about persistence; the service knows nothing about HTTP. Expected
failures travel as exceptions and are translated once, in `GlobalExceptionHandler`.

The token is translated exactly once. `JwtAuthenticationConverter` maps the already-validated claims
onto an `AuthenticatedUser` and publishes that record as the security principal, so a controller
declares `@AuthenticationPrincipal AuthenticatedUser caller` and never touches `Jwt`. The web layer
still owns the coarse `@PreAuthorize` rule; the application layer receives only the identity and the
role and enforces ownership.

Wiring is component-scan based: a feature service lives in `application/<feature>` and is annotated
with `@Service`, an adapter in `infrastructure` is annotated with `@Component`. There is no `@Bean`
method and no configuration class per feature. Configuration classes remain where an infrastructure
object has to be created — `PasswordConfiguration` publishes the password encoder,
`SecurityTokenConfiguration` publishes the signing key, the JWT encoder and the decoder,
`ClockConfiguration` publishes the clock — and `AuthService` depends on the `TokenIssuer` interface,
never on `JwtTokenIssuer`.

## 4. Transactions

| Service method | Transaction |
|---|---|
| `TodoService.create`, `update`, `complete`, `delete` | `@Transactional` |
| `TodoService.get`, `list`, `AuthService.currentUser` | `@Transactional(readOnly = true)` |
| `AuthService.register` | none: one insert, flushed inside the repository transaction |
| `AuthService.login` | none: BCrypt is CPU-bound, the flow writes nothing |

`AuthService.register` inserts exactly one row. The repository's `saveAndFlush` runs in its own short
transaction, and issuing the token is pure computation, so a surrounding transaction would only hold
a connection while the token is built. `AuthService.login` is likewise not transactional: BCrypt is
CPU-bound, and holding a database connection while it runs would occupy a pool slot for the sole
purpose of waiting.

The persistence adapters carry no `@Transactional`; they join the transaction of their caller. There
is no `UnitOfWork` port: every method writes at most one aggregate. The adapters use `saveAndFlush`,
so a unique-constraint violation or an optimistic-lock failure surfaces inside the adapter call,
where it can be translated, instead of at the transaction boundary, where it would be an opaque
error. Both services are non-`final`, because Spring applies `@Transactional` through a CGLIB proxy.

## 5. Error model

| Situation | Type | HTTP | `code` |
|---|---|---|---|
| Request payload invalid | Bean Validation on the body | 400 | `validation.failed` + `fieldErrors` |
| Query or path parameter invalid | Bean Validation on the parameters | 400 | `validation.failed` + `fieldErrors` |
| Unreadable body, type mismatch | Spring MVC exception | 400 | `validation.failed` |
| Domain invariant violated | `DomainException` | 422 | for example `todo.title.invalid` |
| E-mail already registered | `ApplicationException.conflict` | 409 | `auth.email-already-registered` |
| Todo already completed | `ApplicationException.conflict` | 409 | `todo.already-completed` |
| Concurrent modification | `ApplicationException.conflict` from `TodoPersistenceAdapter` | 409 | `concurrency.conflict` |
| Todo missing or not visible | `ApplicationException.notFound` | 404 | `todo.not-found` |
| Wrong credentials | `ApplicationException.unauthorized` | 401 | `auth.invalid-credentials` |
| Missing or invalid token | `AuthenticationException` | 401 | `security.unauthorized` |
| Not authorized | `AccessDeniedException` | 403 | `security.forbidden` |
| Unexpected failure | any other exception | 500 | `internal.error` |

`ApplicationException` carries a stable `code` and an `ErrorType` — `VALIDATION`, `NOT_FOUND`,
`CONFLICT`, `UNAUTHORIZED`, `FORBIDDEN` — and never an HTTP status. The web layer maps the category to
a status. `DomainException` is a plain exception in the `domain` package and is not an `ErrorType`: a
violated invariant is always `422 UNPROCESSABLE_CONTENT`, because the request is well-formed but
breaks a business rule.

`GlobalExceptionHandler` is a `@RestControllerAdvice` with `@Order(Ordered.HIGHEST_PRECEDENCE)` that
extends `ResponseEntityExceptionHandler`. Extending it is what makes the exceptions Spring MVC raises
on its own — a malformed body, a type mismatch, a missing parameter — carry the same `code` and
`correlationId` as the ones the application raises.

Body validation uses `@Valid` on the `@RequestBody` parameter. Query and path parameters carry their
constraints (`@Min`, `@Max`) directly; Spring MVC validates them without a class-level `@Validated`,
which would switch the controller to the older proxy-based mechanism. Both produce
`400 validation.failed` with a `fieldErrors` list. Every problem carries `type`, `title`, `status`,
`detail`, `instance`, `code` and `correlationId`; stack traces and internal messages never appear in a
response.

## 6. Persistence and concurrency

PostgreSQL 18, `spring.jpa.hibernate.ddl-auto=validate` — the schema belongs to Flyway, and Hibernate
refuses to start when the mapping and the schema disagree.

`V1__init.sql` creates exactly two tables:

- `users` — `id uuid` (primary key), `email varchar(320)`, `password_hash varchar(100)`,
  `role varchar(20)`, `created_at timestamptz`; `uq_users_email` is unique on `email`, and
  `ck_users_role` checks `role IN ('USER', 'ADMIN')`.
- `todos` — `id uuid` (primary key), `owner_id uuid`, `title varchar(200)`, `description varchar(2000)`,
  `due_date date`, `status varchar(16)`, `completed_at timestamptz`, `created_at timestamptz`,
  `updated_at timestamptz`, `version bigint`; `fk_todos_owner` references `users (id)` with
  `ON DELETE CASCADE`, and `ck_todos_status_completed_at` binds `status` to `completed_at`.

Four indexes support the list query. The regular list uses `idx_todos_owner_created_at` on
`(owner_id, created_at DESC, id DESC)` or `idx_todos_owner_status_created_at` on
`(owner_id, status, created_at DESC, id DESC)`. The administrator list has no owner predicate, so it
uses `idx_todos_created_at` on `(created_at DESC, id DESC)` or `idx_todos_status_created_at` on
`(status, created_at DESC, id DESC)`. The identifier is the tie-breaker of the sort, not a business
order: it keeps the pages stable when two rows share a timestamp.

- **Two models.** The domain aggregates are plain immutable Java; the JPA entities are a storage model
  that mirrors the table. Explicit `*JpaMapper` components translate in both directions.
- **The status invariant.** `status` is the single source of truth for the completion state, and
  `completedAt` records when the transition happened. The two are bound by one invariant,
  `COMPLETED` if and only if `completedAt != null`. The compact constructor of `Todo` enforces it, and
  `ck_todos_status_completed_at` mirrors it in the database, so the fields cannot drift apart even
  when a row is written behind the application.
- **Optimistic locking is a persistence detail.** `todos.version` is a JPA `@Version` column on
  `TodoJpaEntity`; the domain aggregate carries no revision. The adapter applies a change to the
  managed entity read in the current transaction, so the provider checks the revision that was
  actually read. A stale write affects no row; `TodoPersistenceAdapter` translates the
  `OptimisticLockingFailureException` into `409 concurrency.conflict` at the boundary, so the
  application and web layers stay free of Spring DAO. A row that disappeared after the use case read
  it is reported as `404 todo.not-found`, exactly like a concurrent delete.
- **Assigned identifiers.** `User` receives its UUID from the domain. `UserJpaEntity` implements
  `Persistable<UUID>`, so Spring Data knows the entity is new and issues a single insert instead of a
  select followed by a merge.
- **The unique constraint is the authority.** Registration does not check the e-mail address first:
  `uq_users_email` is checked by the database, so there is no time-of-check/time-of-use window and no
  extra query. `UserPersistenceAdapter` inspects the Hibernate constraint name, translates that one
  known constraint into `409 auth.email-already-registered` and rethrows every other integrity
  violation unchanged.
- **Read model.** `TodoPersistenceAdapter` implements both `TodoRepository` and `TodoQueryPort`: the
  same table and the same mapper back the write side and the read side, so splitting them only adds a
  class. The query side uses the JPA Criteria API, applies the fixed `createdAt DESC, id DESC` order
  and maps the page of entities onto a `PageResult<TodoView>`. The identifier is the deterministic
  tie-breaker: two rows created in the same millisecond cannot swap places between requests. The
  application passes only the page index and size, so it does not depend on a persistence property
  name or on Spring Data's pagination types. A client-supplied sort would need a whitelist and a
  second index.

## 7. Security

- **Access tokens only.** The API issues an HS256 JWT with the claims `iss`, `aud`, `sub`, `iat`,
  `nbf`, `exp` and `roles` (exactly one role, no `jti`, no e-mail address). The lifetime is 60
  minutes. There is no refresh token, no cookie, no session, no logout endpoint and therefore no CSRF
  surface. The `Authorization` header is never attached by the browser automatically, so a cross-site
  request cannot ride on the credentials of the user.
- **Every claim is validated.** The decoder checks the issuer, the audience, the timestamps, the
  subject (a UUID) and the role claim. A token with a malformed subject is rejected with a `401`
  before it reaches a controller.
- **The signing key** is a Base64 secret of at least 32 bytes. `SecurityTokenConfiguration` publishes
  the key as a profile-scoped bean: under `local` it generates an ephemeral one at startup, under
  every other profile it reads `JWT_SECRET` and fails the startup without it. There is no configuration
  flag that could switch the ephemeral key on in a deployment.
- **Passwords** are hashed with BCrypt (default cost 10). `RawPassword` enforces the 72-byte BCrypt
  limit at the type level: `forRegistration` also requires 12 characters, `forLogin` only the byte
  limit, so accounts that predate the rule keep working. `PasswordHash` accepts BCrypt values with a
  cost factor of 4 to 31 only and redacts its `toString()`.
- **Roles** are `USER` and `ADMIN`, stored in one `users.role` column. There are no RBAC tables. The
  web layer checks `isAuthenticated()` with `@PreAuthorize`; the application layer enforces ownership:
  a regular user manages its own todos, an administrator reads every todo but writes only its own.
- **Non-disclosure.** A resource the caller may not see is reported as `404`, never as `403`, so its
  existence is not disclosed.
- **Transport.** CORS is an explicit origin allow-list without credentials; HSTS,
  `X-Content-Type-Options`, `X-Frame-Options: DENY` and `Referrer-Policy: no-referrer` are set, and
  the token responses carry `Cache-Control: no-store`.
- **Registration enumeration.** A second registration returns `409 auth.email-already-registered`,
  which reveals whether an address is in use. It is listed as accepted risk R5 in
  [`SECURITY.md`](../SECURITY.md); the fix is a verification e-mail or a response that does not
  distinguish the two cases.
- **No in-application rate limiting.** The credential endpoints are not throttled; that is the job of
  the edge or the WAF, and the template documents this as an accepted risk.
- **Deployment surface.** The `prod` profile disables `/v3/api-docs` and `/scalar`. The health probes
  stay public for the orchestrator; every other actuator endpoint is authenticated.

## 8. Configuration

Every property is a validated `@ConfigurationProperties` record. `ApiProperties` constrains
`problem-base-uri` and `public-base-url` as non-blank URLs with an `http` or `https` scheme;
`JwtProperties` validates the issuer, audience, lifetime and the Base64 secret;
`PasswordProperties` keeps the BCrypt cost between 4 and 31; `CorsProperties` rejects a wildcard
origin with credentials. An invalid value stops the startup. `ValidConfigurationTest` starts the
shipped configuration as a whole, `ConfigurationPropertiesValidationTest` covers every invalid case,
and `ProductionConfigurationTest` starts the `prod` profile and proves that a missing deployment value
fails.

The `local` profile adds the Compose-managed PostgreSQL and the ephemeral JWT key, and imports an
optional `.env` file. The `prod` profile requires the database settings, `JWT_SECRET`,
`JWT_ISSUER`, `JWT_AUDIENCE`, `PROBLEM_BASE_URI`, `PUBLIC_BASE_URL` and `CORS_ALLOWED_ORIGINS`; it
disables the API explorer. The binder leaves an unresolved placeholder in place, so the configuration
records explicitly reject a value that still looks like `${NAME}`; a missing deployment variable
therefore fails the startup instead of becoming a literal issuer or URL. Secrets come from
environment variables, never from a committed file.

`spring.threads.virtual.enabled` is on. Java 25 with blocking MVC and JDBC benefits from virtual
threads: a request that waits for the database releases its carrier thread instead of occupying a
platform thread from a pool sized for CPUs.

## 9. Tests

| Level | Location | What it proves |
|---|---|---|
| Domain unit | `src/test/java/.../domain` | Value-object rules, aggregate invariants, immutability |
| Service unit | `src/test/java/.../application` | Branching, ownership rules, error contracts, with the hand-written fakes |
| Architecture | `src/test/java/.../architecture` | The layer boundaries of section 2 |
| Web slice | `src/test/java/.../web` | `MockMvcTester` against the production `SecurityConfiguration`: status codes, validation, security, `ProblemDetail` |
| Configuration | `src/test/java/.../config` | The shipped configuration binds, an invalid value stops the startup, and the `prod` profile refuses a missing deployment value |
| Integration | `src/integrationTest` | The whole application against PostgreSQL 18 in a Testcontainer: migrations, constraints, optimistic locking, CORS and the HTTP flows end to end, including a token signed by another key |

The fakes live in `src/testFixtures/java` and are shared by the unit, slice and integration tests. The
web slice imports the production `SecurityConfiguration`; only a `JwtDecoder` is stubbed, because the
tests install an authentication that the production converter created. The integration suite shares
one container through the singleton pattern, so the Spring context cache stays effective. The suite
stays lean: one test per distinct behaviour.

`./gradlew check` is Docker-free: it runs `spotlessCheck`, the tests above and the single coverage gate
— 70 % line coverage for `domain` and `application`. `./gradlew integrationTest` needs Docker and runs
the Testcontainers suite. There is no second gate.

The same commands run in CI. The `CI` workflow executes `spotlessCheck check`, builds the executable
jar and runs the Testcontainers suite in a separate job with Docker; the `Template` workflow copies
the repository, renames it and verifies that the renamed copy still passes the checks. `main` is
protected by a ruleset that requires those checks and allows only squash merges.

## 10. Key decisions

- **One deployable monolith, layers as packages.** There is no multi-module build, no Spring Modulith
  and no microservice infrastructure. Ports exist only at real boundaries: `UserRepository` and
  `TodoRepository` in `domain`, `TodoQueryPort` and `TokenIssuer` in `application`. Extracting modules
  or services later remains possible without rewriting the domain model.
- **Two services, no interface per use case.** `AuthService` and `TodoService` are `@Service` classes
  whose public methods are the use cases, and they take plain parameters instead of command records.
  A `*UseCase` interface with one implementation per operation costs four files per endpoint and buys
  testability the hand-written fakes already provide. Interfaces are kept where there is a real
  boundary the application does not own: `UserRepository`, `TodoRepository`, `TodoQueryPort` and
  `TokenIssuer`.
- **Exceptions over results.** Expected business outcomes are `ApplicationException` values created
  through static factories; invariant violations are `DomainException`; anything else propagates. The
  web layer translates them once into RFC 9457 `ProblemDetail`. There is no `Result` monad and no
  `HttpStatus` in the application layer.
- **The application owns its pagination.** `PageResult<T>` carries the items, the page index, the size
  and the total element count; `totalPages()` and `hasNext()` are derived. Spring Data's
  `Page`/`Pageable` stay inside `TodoPersistenceAdapter`, so neither the services nor the web layer
  depend on a persistence library.
- **Separate JPA entities and domain aggregates.** The domain stays plain, immutable Java and enforces
  its invariants in constructors; the persistence model mirrors the table and never leaves
  `infrastructure`. Optimistic locking stays on the JPA entity, and the completion invariant is
  mirrored by a database check constraint.
- **Stateless JWT authentication.** One HS256 access token in the `Authorization` header, no cookie,
  no refresh token, no logout endpoint and therefore no CSRF surface. The symmetric key keeps the
  keystore and JWKS out of the template; RS256 with JWKS is the documented next step when a second
  service verifies the tokens. The accepted risks are listed in [`SECURITY.md`](../SECURITY.md).
