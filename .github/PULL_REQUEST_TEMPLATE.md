## Summary

<!-- What changed and why. Link the issue: "Closes #123". -->

## Checklist

- [ ] `./gradlew spotlessApply check` passes.
- [ ] `./gradlew integrationTest` passes when the change touches the database or the HTTP flow.
- [ ] New behaviour is covered by a unit test and, where relevant, by a web slice and an integration test.
- [ ] A Flyway migration accompanies every schema change.
- [ ] `README.md`, `docs/architecture.md` and `api.http` are updated when the public surface changes.
