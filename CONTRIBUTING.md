# Contributing

Thanks for taking the time to improve the template. This repository is a **template**: every change
is copied into the projects that start from it, so a change must be worth carrying forever. When in
doubt, open an issue before writing code.

## Prerequisites

- **JDK 25.** The Gradle toolchain pins the language and runtime version; an older JDK fails the
  build with a toolchain error.
- **Docker** for the integration suite. Unit, web slice and architecture tests run without it.
- No local database: Testcontainers starts PostgreSQL 18 for `integrationTest`, and Compose starts
  it for `bootRun` with the `local` profile.

On Windows, point `JAVA_HOME` at the JDK 25 installation before running Gradle:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.5.0.3'
```

## Workflow

1. Fork the repository and branch from `main`. Keep a branch to one concern.
2. Make the change and add the tests described below.
3. Format and verify locally:

   ```bash
   ./gradlew spotlessApply
   ./gradlew check              # formatting, unit, slice and architecture tests, coverage gate
   ./gradlew integrationTest    # Testcontainers; requires Docker
   ```

4. Push the branch and open a pull request. Fill in the template and link the issue it resolves.
5. Wait for the three required checks — formatting and unit tests, the Testcontainers suite and the
   template-rename verification — and squash-merge your own pull request.

`check` is intentionally Docker-free so that it can run in a restricted environment. A change that
needs a database is only complete when `integrationTest` also passes.

`main` carries a repository ruleset: a pull request is required, the three checks above have to pass,
only squash merges are allowed, and the branch can neither be force-pushed nor deleted. A single
maintainer does not need a second reviewer — the pull request is approved and merged by its author.
The repository administrator can bypass the rule in an emergency, for example when a broken build has
to be repaired before the checks can pass again.

## What a change must include

- **Tests.** Every new service method gets a unit test with the fakes from `src/testFixtures`; every
  new controller a web slice test with `MockMvcTester`; behaviour that touches PostgreSQL an
  integration test.
- **A Flyway migration** for every schema change. Never edit an applied migration: add
  `V<n>__description.sql`. The template's own `V1` may be rewritten only while the template is
  unreleased.
- **A validation test** for a new configuration property, including an invalid value.
- **Architecture coverage** — keep `ArchitectureTest` compact: one rule per boundary a reader can see
  in the package structure. A new boundary gets its rule; the suite does not need a violation fixture
  per rule.
- **Documentation.** Update `docs/architecture.md` when a design decision changes and `README.md` when
  the public surface or configuration changes.
- **The API contract.** New or changed endpoints update the OpenAPI annotations and `api.http`.

The binding rules are in [`docs/architecture.md`](docs/architecture.md); the layer boundaries are not
style advice: they are enforced by `ArchitectureTest` and a violation fails the build.

## Dependencies

Dependabot opens one grouped pull request per ecosystem and week: one for Gradle, one for GitHub
Actions. Merge them like any other change — the checks have to pass first. Read the release notes
of a major version before merging it; the template pins the versions it was verified with.

## Style

- Palantir Java Format through Spotless: 4 spaces, 120 columns, explicit imports.
- No Lombok, no MapStruct, no annotation-processor magic. Records for immutable data.
- Keep the layers as they are: the domain is plain Java; the application layer may use Spring for
  wiring, transactions, password hashing and token signing, but never JPA, HTTP, Jackson or Bean
  Validation.
- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/): `feat:`,
  `fix:`, `docs:`, `test:`, `build:`, `chore:`, with an optional scope such as `fix(security):`.
  Use the imperative mood and keep the subject within 72 characters.
- Separate refactoring from behaviour changes. Small, reviewable pull requests are merged faster.

## Template changes

- Verify a rename in a scratch copy before submitting a change that touches packaging, configuration
  or the build:

  ```bash
  ./gradlew renameProject -Pgroup=com.example -Partifact=demo -Ppackage=com.example.demo
  ```

  The copy must still pass `spotlessCheck check`; the `Template` workflow does this on CI.
- Resist scope creep. Features that a real service can add in an afternoon — caching, messaging,
  API versioning, a second authentication mechanism — are listed as out of scope on purpose.
- When you add a convention, encode it in `ArchitectureTest` or in the agent skills rather than in a
  paragraph alone.

## Security

Do not report a vulnerability in a public issue. Follow [`SECURITY.md`](SECURITY.md) and use a private
security advisory.

## AI-assisted contributions

AI assistance is welcome. You remain responsible for the change: review every line, run the tests,
and disclose substantial AI-generated code in the pull request description. The agent skills under
`.agents/skills` describe the conventions those tools are expected to follow.

## License

The template is released under the [MIT License](LICENSE). Contributions are accepted under the same
terms (inbound = outbound); there is no CLA to sign.
