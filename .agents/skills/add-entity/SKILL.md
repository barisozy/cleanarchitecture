---
name: add-entity
description: Add a new domain aggregate to this Clean Architecture template — aggregate and value objects, repository interface, JPA entity, mapper, persistence adapter, Flyway migration and tests. Use when the user asks to add an entity, aggregate, domain model or table.
argument-hint: <entity description, e.g. "Project with a name and an owner">
---

# Add a Domain Aggregate

Create a new aggregate and wire it through every layer, following the `Todo` pattern. The domain
model is framework free: no Spring, no JPA, no Jackson, no clock reads.

## Files to create or modify

1. **Aggregate** — `src/main/java/com/barisozy/cleanarchitecture/domain/<feature>/<Entity>.java`

The template models aggregates as immutable records with a compact constructor that enforces the
invariants, a static factory for creation and one method per state transition.

```java
package com.barisozy.cleanarchitecture.domain.project;

import static java.util.Objects.requireNonNull;

import com.barisozy.cleanarchitecture.domain.user.UserId;
import com.barisozy.cleanarchitecture.domain.DomainException;
import java.time.Instant;

/**
 * The project aggregate. Every mutation returns a new instance, so a state transition is always an
 * explicit expression of the business rule it implements.
 */
public record Project(ProjectId id, UserId ownerId, ProjectName name, Instant createdAt, Instant updatedAt) {

    public Project {
        requireNonNull(id, "id");
        requireNonNull(ownerId, "ownerId");
        requireNonNull(name, "name");
        requireNonNull(createdAt, "createdAt");
        requireNonNull(updatedAt, "updatedAt");
    }

    /** Creates a project. */
    public static Project create(UserId ownerId, ProjectName name, Instant now) {
        return new Project(ProjectId.newId(), ownerId, name, now, now);
    }

    /** Renames the project. */
    public Project rename(ProjectName newName, Instant now) {
        return new Project(id, ownerId, newName, createdAt, now);
    }

    public boolean isOwnedBy(UserId userId) {
        return ownerId.equals(userId);
    }
}
```

Rules: the compact constructor enforces the invariants and throws a `DomainException` with a stable
code; every mutation returns a new instance; there are no setters and no mutable collections; the
aggregate never reads the clock — the `Instant` is always passed in.

2. **Value objects and ids** — `domain/<feature>/`: `ProjectId`, `ProjectName`, ... They validate
   themselves and throw `DomainException` with a stable code.

```java
public record ProjectName(String value) {

    public ProjectName {
        if (value == null || value.isBlank() || value.length() > 120) {
            throw new DomainException("project.invalid-name", "Project name must not be blank.");
        }
    }
}
```

An id is a record around a `UUID`:

```java
public record ProjectId(UUID value) {

    public ProjectId {
        if (value == null) {
            throw new IllegalArgumentException("projectId must not be null");
        }
    }

    public static ProjectId newId() {
        return new ProjectId(UUID.randomUUID());
    }

    public static ProjectId from(UUID value) {
        return new ProjectId(value);
    }
}
```

3. **Repository interface** — `domain/<feature>/<Entity>Repository.java`. The domain owns the
   interface; the infrastructure owns the implementation. The write methods state their intent; the
   revision that conditions an update is a persistence detail of the adapter.

```java
public interface ProjectRepository {

    Optional<Project> findById(ProjectId id);

    /** Stores a new aggregate and returns it with the revision the database assigned. */
    Project insert(Project project);

    /** Writes an existing aggregate under an optimistic-lock condition. */
    Project update(Project project);

    /** Deletes an existing aggregate under an optimistic-lock condition. */
    void delete(Project project);
}
```

4. **JPA entity** — `src/main/java/com/barisozy/cleanarchitecture/infrastructure/persistence/<feature>/<Entity>JpaEntity.java`.
   It is a mutable persistence model, lives only in `infrastructure.persistence` and is never
   returned to the application layer.

```java
@Entity
@Table(name = "projects")
public class ProjectJpaEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected ProjectJpaEntity() {}
    // constructor, getters and an updateFrom(...) method
}
```

Use `@Version` for optimistic locking: the column lives on the JPA entity, the aggregate does not
carry it. Nullable columns use `@Nullable` on the field type (JSpecify) and `@Column` without
`nullable = false`.

5. **Mapper** — `infrastructure/persistence/<feature>/<Entity>JpaMapper.java`. A hand-written
   `@Component` that converts between the entity and the aggregate. No MapStruct.

```java
@Component
public class ProjectJpaMapper {

    public Project toDomain(ProjectJpaEntity entity) {
        return new Project(
                ProjectId.from(entity.getId()),
                new UserId(entity.getOwnerId()),
                new ProjectName(entity.getName()),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    public ProjectJpaEntity toEntity(Project project) {
        return new ProjectJpaEntity(
                project.id().value(),
                project.ownerId().value(),
                project.name().value(),
                project.createdAt(),
                project.updatedAt());
    }
}
```

6. **Spring Data repository** — `infrastructure/persistence/<feature>/<Entity>JpaRepository.java`,
   extending `JpaRepository`. It is never used outside the persistence package.

7. **Persistence adapter** — `infrastructure/persistence/<feature>/<Entity>PersistenceAdapter.java`.
   It implements the domain repository interface, delegates to the JPA repository and the mapper. When
   the feature also needs a read model, the same adapter implements the application's `*QueryPort`; one
   table and one mapper do not justify two classes. It carries **no** `@Transactional`: the transaction
   belongs to the feature service.

```java
@Component
public class ProjectPersistenceAdapter implements ProjectRepository {

    private final ProjectJpaRepository repository;
    private final ProjectJpaMapper mapper;

    @Override
    public Optional<Project> findById(ProjectId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Project insert(Project project) {
        // saveAndFlush so that a constraint violation surfaces inside this call.
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(project)));
    }

    @Override
    public Project update(Project project) {
        // Re-read the managed entity so that Hibernate performs the optimistic-lock check.
        ProjectJpaEntity entity = repository
                .findById(project.id().value())
                .orElseThrow(() -> new IllegalStateException("project not found: " + project.id().value()));
        entity.updateFrom(project.name().value(), project.updatedAt());
        return mapper.toDomain(repository.saveAndFlush(entity));
    }
}
```

8. **Migration** — `src/main/resources/db/migration/V<n>__<description>.sql`. Flyway migrations are
   append only: never edit an already applied migration, always add a new one. The template's own
   `V1` may be rewritten while the template is not released yet.

```sql
CREATE TABLE projects (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_projects_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_projects_owner_created_at ON projects (owner_id, created_at DESC);
```

9. **Tests** — a domain unit test for the aggregate and its invariants
   (`src/test/java/com/barisozy/cleanarchitecture/domain/<feature>/<Entity>Test.java`) and, when the
   persistence matters, an integration test in
   `src/integrationTest/.../`. Keep both lean: one test per distinct behaviour.

## Rules

- The domain package references only the JDK and JSpecify — no Spring, no JPA, no Jackson.
- Persistence details (keys, conversions, relationships) live in the JPA entity, never on the
  aggregate.
- `@Transactional` is only allowed in the `application` layer, on a feature service.
- JPA entities live only in `infrastructure.persistence`; `*Repository` interfaces live in `domain`.
- Run `./gradlew spotlessApply check` when done; the ArchUnit rules enforce the layer boundaries.
- If the user also wants an endpoint for the aggregate, continue with the `add-feature` skill.
