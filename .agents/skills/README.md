# Clean Architecture Agent Skills

A skill pack that teaches an AI coding agent the conventions of this Clean Architecture template — so
every feature it builds looks like it was written by the maintainers: one service per feature with an
exception-based error model, hand-written domain aggregates, Spring MVC controllers that map to RFC
9457 problem details, and a lean, exemplary test suite.

## What's inside

| Skill | Invoke with | What it does |
|---|---|---|
| **add-feature** | `/add-feature archive a todo item` | Adds a feature end to end: domain behaviour, a method on the feature service, the controller, DTOs, a migration when needed, and unit + web slice + integration tests. |
| **add-entity** | `/add-entity Project with a name and owner` | Adds a domain aggregate end to end: aggregate, value objects, repository interface, JPA entity, mapper, adapter, Flyway migration. |
| **add-tests** | `/add-tests TodoService.complete` | Backfills service unit tests, web slice tests and integration tests for existing behaviour. |
| **ca-review** | `/ca-review` | Reviews pending changes against the template's conventions: layer boundaries, error handling, wiring, security and test coverage. |

You don't have to invoke them explicitly — an agent that supports skills picks the right one
automatically when you say things like "add an endpoint to snooze a todo".

## Installation

The skills live in `.agents/skills/`. If you cloned the template, they are already active — just open
the repository in your agent.

To use them in another project based on this template, copy the folder:

```
your-project/
└── .agents/
    └── skills/
        ├── add-feature/
        ├── add-entity/
        ├── add-tests/
        └── ca-review/
```

## Try it

```
/add-feature snooze a todo until a given date
```

The agent adds the domain behaviour and the service method, the controller with its request/response
DTOs, the `@Service` wiring, and the three test types — then runs `./gradlew spotlessApply check`.

## Customizing

Each skill is a plain Markdown file (`SKILL.md`). Renamed a layer, prefer a different error model, use
another test stack? Edit the file once and every future feature follows suit. The skills are the
executable version of your team's conventions.

The authoritative conventions live in `docs/architecture.md` and `CONTRIBUTING.md`. When the skills and the
code disagree, the code wins: update the skills together with the change.
