# Clean Architecture — Minimal Java Template

[![CI](https://github.com/barisozy/cleanarchitecture/actions/workflows/ci.yml/badge.svg)](https://github.com/barisozy/cleanarchitecture/actions/workflows/ci.yml)
[![Template](https://github.com/barisozy/cleanarchitecture/actions/workflows/template.yml/badge.svg)](https://github.com/barisozy/cleanarchitecture/actions/workflows/template.yml)
[![MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Java 25](https://img.shields.io/badge/java-25-ED8B00?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![Spring Boot 4.1](https://img.shields.io/badge/Spring%20Boot-4.1.2-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Gradle 9.9](https://img.shields.io/badge/Gradle-9.9.1-02303A?logo=gradle&logoColor=white)](https://gradle.org/)
[![PostgreSQL 18](https://img.shields.io/badge/PostgreSQL-18-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)

A small, production-minded starting point for a **Java 25 / Spring Boot 4.1.2** HTTP service. It
ships the parts every project needs on day one — register/login, JWT access tokens, todos with
ownership rules, PostgreSQL, RFC 9457 errors, OpenAPI and tests — and stays deliberately easy to
read.

The dependency rule is executable: `ArchitectureTest` (ArchUnit) fails the build when a layer reaches
where it must not. The domain is plain Java. The application layer uses Spring only for wiring, the
transaction boundary, password hashing and token issuance through a port.

## What is in the box

One Gradle project, one Spring Boot application, one PostgreSQL database. The layers are **packages**,
not modules:

```text
com.barisozy.cleanarchitecture
├── domain           aggregates, value objects, invariants, repository interfaces
├── application      services, views, ports
├── infrastructure   JPA adapters, security adapters, configuration
└── web              controllers, DTOs, security rules, error handling, OpenAPI
```

Two aggregates (`User`, `Todo`), nine endpoints, four ports (`UserRepository`, `TodoRepository`,
`TodoQueryPort`, `TokenIssuer`) and one Flyway migration. No background jobs, no cache, no message
broker.

## Stack

Java 25 · Spring Boot 4.1.2 · Gradle 9.9.1 · Hibernate 7.4 · PostgreSQL 18 · Flyway ·
springdoc-openapi 3 with Scalar · JUnit 6, AssertJ, Mockito, ArchUnit, Testcontainers · Spotless
(Palantir Java Format) · Error Prone · JaCoCo. No Lombok, no MapStruct.

## Quick start

Requires **JDK 25** and **Docker**.

```bash
docker compose up -d postgres
./gradlew bootRun --args='--spring.profiles.active=local'
```

| URL | Purpose |
|---|---|
| <http://localhost:8080/scalar> | Scalar API explorer |
| <http://localhost:8080/v3/api-docs> | OpenAPI document |
| <http://localhost:8080/actuator/health> | Health probe |

The `local` profile seeds two development accounts on first start:

| Account | Password | Role |
|---|---|---|
| `demo@example.com` | `DemoPassword123!` | `USER` |
| `admin@example.com` | `AdminPassword123!` | `ADMIN` |

They exist in the `local` profile only. [`api.http`](api.http) holds ready-to-run request examples.
The `local` profile imports an optional `.env` file; copy [`.env.example`](.env.example) if you want
to change the defaults. To run the API in Docker as well:
`docker compose --profile app up --build -d`.

## Architecture in one minute

```text
HTTP ──▶ web ──▶ application ──▶ domain
                      ▲
                      │ implements the ports
                infrastructure
```

| Layer | Contains | May depend on |
|---|---|---|
| `domain` | aggregates, value objects, invariants, `*Repository` interfaces | JDK, JSpecify |
| `application` | `AuthService`, `TodoService`, views, ports | `domain`, Spring stereotypes, `@Transactional`, `PasswordEncoder`, `Clock` |
| `infrastructure` | JPA entities, mappers, adapters, JWT encoder/decoder, configuration | everything below it |
| `web` | controllers, DTOs, security rules, `ProblemDetail` handling, OpenAPI | `application`, allowed domain value types, Spring Web/Security, Bean Validation |

A request travels through four small steps:

1. The **controller** turns HTTP into a method call on a service and passes the caller identity.
2. The **service** applies the business flow and the ownership rules, then calls a domain repository
   or a read port.
3. The **adapter** implements the port with JPA, JWT or another library.
4. The **controller** maps the returned view back to a response DTO.

There is one `@Service` per feature — `AuthService` registers, logs in and reads the current profile;
`TodoService` creates, reads, lists, updates, completes and deletes. They take plain method
parameters instead of command objects, because a six-method service with a dozen one-field records
is harder to read than the methods themselves. Expected failures are `ApplicationException` values,
invariant violations are `DomainException`, and `GlobalExceptionHandler` translates both — once —
into a `ProblemDetail`.

Pagination uses the application's own `PageResult<T>`. Spring Data `Page`/`Pageable` stay inside the
persistence adapter, so the services and the web layer do not depend on a persistence library. Token
signing works the same way: the application declares `TokenIssuer`, infrastructure implements it with
Spring Security's `JwtEncoder`. `spring.threads.virtual.enabled` is on, so blocking MVC and JDBC work
does not consume one platform thread per request.

The full design, decisions and trade-offs are in [`docs/architecture.md`](docs/architecture.md).

## API

| Method | Path | Access | Request | Response |
|---|---|---|---|---|
| `POST` | `/api/auth/register` | public | `{email, password}` | `201` `{accessToken, tokenType, expiresIn, user}` |
| `POST` | `/api/auth/login` | public | `{email, password}` | `200` same body |
| `GET` | `/api/auth/me` | Bearer | — | `200` `{id, email, role}` |
| `POST` | `/api/todos` | Bearer | `{title, description?, dueDate?}` | `201` todo + `Location` |
| `GET` | `/api/todos/{id}` | Bearer | — | `200` todo |
| `GET` | `/api/todos` | Bearer | `?completed=&page=&size=` | `200` page of todos |
| `PUT` | `/api/todos/{id}` | Bearer | `{title, description?, dueDate?}` | `200` todo |
| `POST` | `/api/todos/{id}/complete` | Bearer | — | `200` todo |
| `DELETE` | `/api/todos/{id}` | Bearer | — | `204` |

A todo is published as
`{id, ownerId, title, description, completed, dueDate, completedAt, createdAt, updatedAt}`. The
`completed` flag is derived from the domain status, so two fields cannot disagree.

A page is published as `{items, page, size, totalElements, totalPages, hasNext}`. `page` starts at 0,
`size` is 20 by default and may be between 1 and 100. The read model returns the newest todo first,
with the identifier as a deterministic tie-breaker; there is no `sort` or `search`.

### Authentication

1. `POST /api/auth/register` or `POST /api/auth/login` returns the access token in the body.
2. Every following call sends `Authorization: Bearer <access token>`.
3. The token expires after 60 minutes; the client logs in again.

The API issues one credential: an HS256 JWT with the claims `iss`, `aud`, `sub`, `iat`, `nbf`, `exp`
and `roles` (one role, no `jti`, no e-mail). The decoder validates the issuer, the audience, the
timestamps, the subject (a UUID) and the role claim. There is no cookie, session, refresh token or
logout endpoint, so there is no CSRF surface.

| Role | Reads own todos | Writes own todos | Reads every todo | Writes every todo |
|---|---|---|---|---|
| `USER` | yes | yes | no | no |
| `ADMIN` | yes | yes | yes | no |

The web layer checks *who* the caller is (`@PreAuthorize("isAuthenticated()")`); the application layer
enforces *whose* resource it is. A todo the caller may not see is reported as `404`, never `403`, so
its existence is not disclosed.

### Errors

Errors are RFC 9457 `ProblemDetail` documents:

```json
{
  "type": "https://api.example.com/problems/todo.not-found",
  "title": "Not Found",
  "status": 404,
  "detail": "Todo not found.",
  "instance": "/api/todos/3f1c...",
  "code": "todo.not-found",
  "correlationId": "4a1d..."
}
```

| Situation | Type | Status | `code` |
|---|---|---|---|
| Request payload or parameter invalid | Bean Validation | 400 | `validation.failed` (+ `fieldErrors`) |
| Domain invariant violated | `DomainException` | 422 | e.g. `todo.title.invalid` |
| E-mail already registered | `ApplicationException.conflict` | 409 | `auth.email-already-registered` |
| Todo already completed | `ApplicationException.conflict` | 409 | `todo.already-completed` |
| Concurrent modification | `ApplicationException.conflict` | 409 | `concurrency.conflict` |
| Todo missing or not visible | `ApplicationException.notFound` | 404 | `todo.not-found` |
| Wrong credentials | `ApplicationException.unauthorized` | 401 | `auth.invalid-credentials` |
| Missing or invalid token | `AuthenticationException` | 401 | `security.unauthorized` |
| Not authorized | `AccessDeniedException` | 403 | `security.forbidden` |
| Unexpected failure | any other exception | 500 | `internal.error` |

Clients branch on `code`, never on `detail`; a response never contains a stack trace or an internal
message.

## Tests and quality gates

| Command | Docker | Covers |
|---|---|---|
| `./gradlew spotlessCheck` | no | formatting of Java, Markdown, YAML, Kotlin DSL and workflows |
| `./gradlew test` | no | domain, service, web slice and architecture tests |
| `./gradlew check` | no | `spotlessCheck` + the tests + the coverage gate (70 % line coverage for `domain` and `application`) |
| `./gradlew integrationTest` | yes | Testcontainers against PostgreSQL 18: migrations, constraints, optimistic locking, HTTP flows end to end |
| `./gradlew build` | no | `check` plus the executable jar |

The standard Gradle directories keep the layout familiar:

| Directory | Content |
|---|---|
| `src/test/java` | domain unit tests, service tests, `MockMvcTester` slices, architecture tests, configuration tests |
| `src/testFixtures/java` | hand-written fakes and test data, shared by the unit and slice tests |
| `src/integrationTest/java` | Testcontainers tests against a real PostgreSQL |
| `src/test/resources` | the `test` profile, also reused by the integration tests |

`./gradlew check` is Docker-free. `./gradlew integrationTest` needs Docker. There is exactly one
coverage gate.

## Repository automation

| Workflow | Trigger | What it does |
|---|---|---|
| `CI` | push and pull request | `spotlessCheck check`, the coverage gate, the executable jar, and the Testcontainers suite in a separate job with Docker |
| `Template` | push and pull request | copies the repository, runs `renameProject` and verifies that the renamed copy still passes the checks |

`main` is protected by a repository ruleset: a pull request is required, the three checks above have
to pass, only squash merges are allowed, and force-pushes and branch deletion are blocked. The rule
needs no second reviewer — the maintainer opens, approves and merges their own pull request — and the
repository administrator can bypass it in an emergency.

Dependabot watches Gradle and GitHub Actions once a week and opens one grouped pull request per
ecosystem. A dependency update is merged like any other change: let the checks run and squash-merge.

## Using this as a template

```bash
./gradlew renameProject -Pgroup=com.example -Partifact=demo -Ppackage=com.example.demo
./gradlew spotlessApply check
```

The task rewrites `settings.gradle.kts`, the `group`, the source directories and package declarations
under `src/main/java`, `src/test/java`, `src/testFixtures/java` and `src/integrationTest/java`, the
artifact name in the text files (including the Java test data and the Compose service names),
`spring.application.name` and the README title.
It also deletes `.github/workflows/template.yml`, the workflow that only verifies the template itself.
Afterwards review `README.md`, `docker-compose.yaml`, the JWT issuer/audience, the CORS origins and
the badge links, which still point to the template repository; reset an old local schema with
`docker compose down -v`.

## Configuration

Every secret comes from the environment; see [`.env.example`](.env.example).

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | local values | Database connection; mandatory in `prod` |
| `JWT_ISSUER`, `JWT_AUDIENCE` | `https://api.example.com`, `cleanarchitecture-api` | Token issuer and audience; mandatory in `prod` |
| `JWT_ACCESS_TOKEN_TTL` | `PT1H` | Access token lifetime |
| `JWT_SECRET` | empty | Base64 secret of at least 32 bytes; mandatory outside `local` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173` | Allowed browser origins; mandatory in `prod` |
| `PROBLEM_BASE_URI` | `https://api.example.com/problems` | Base URI of `ProblemDetail.type`; mandatory in `prod` |
| `PUBLIC_BASE_URL` | `http://localhost:8080` | Public base URL, published in OpenAPI; mandatory in `prod` |
| `BCRYPT_STRENGTH` | `10` | BCrypt cost factor of newly hashed passwords (4–31) |
| `DOCKER_COMPOSE_ENABLED` | `true` in `local` | Start PostgreSQL from Compose |

The property records are validated at startup: a blank or malformed URL, a missing JWT claim, an
out-of-range BCrypt cost and a non-positive token lifetime fail fast. The `local` profile generates an
ephemeral signing key at startup, so every restart invalidates the issued tokens; every other profile
requires `JWT_SECRET` and refuses to start without it.

The `prod` profile has no `example.com` or `localhost` fallback. It also disables `/v3/api-docs` and
`/scalar`.

## Deliberately out of scope

Refresh tokens and cookie transport, CSRF protection, JWKS/RS256 key management, database-backed RBAC
tables, in-application rate limiting, API versioning, notifications, soft delete, multi-tenancy,
background jobs, caching, a message broker, dependency locking/verification, an SBOM and
CodeQL/Trivy pipeline jobs. Each belongs in a project that needs it, with its own tests.

## Documentation

- [`docs/architecture.md`](docs/architecture.md) — request flow, transactions, error model,
  persistence, security, tests and key decisions.
- [`SECURITY.md`](SECURITY.md) — security model and accepted risks.
- [`CONTRIBUTING.md`](CONTRIBUTING.md) — build prerequisites and test expectations.
- [`LICENSE`](LICENSE).
