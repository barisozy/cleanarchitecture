package com.barisozy.cleanarchitecture.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.hibernate.autoconfigure.HibernateProperties;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Covers the schema: the migrations Flyway applied and the constraints the database enforces.
 *
 * <p>The check constraint of the todos mirrors the invariant of the aggregate. The database is the
 * last line of defence, so the constraint has to reject the combinations the domain forbids even
 * when the row is written behind the application.
 */
class FlywayMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private HibernateProperties hibernateProperties;

    /**
     * The context starts with {@code ddl-auto=validate}: Hibernate compares the mapping with the
     * schema of the database and refuses to start when they differ. The fact that the context
     * starts at all is therefore part of this assertion.
     */
    @Test
    void appliesEveryMigrationAndCreatesTheTables() {
        List<Map<String, Object>> history =
                jdbcTemplate.queryForList("SELECT version, description, type, success FROM flyway_schema_history");

        assertThat(history)
                .isNotEmpty()
                .allSatisfy(migration -> assertThat(migration.get("success")).isEqualTo(true));
        Map<String, Object> initial = history.stream()
                .filter(migration -> "1".equals(String.valueOf(migration.get("version"))))
                .findFirst()
                .orElseThrow(() -> new AssertionError("V1 migration is missing from the history"));
        assertThat(initial.get("description")).isEqualTo("init");

        Integer tables = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name IN ('users', 'todos')",
                Integer.class);
        assertThat(tables).isEqualTo(2);

        assertThat(hibernateProperties.getDdlAuto()).isEqualTo("validate");
    }

    /** The database rejects the two broken combinations and accepts the two valid ones. */
    @Test
    void enforcesTheStatusAndCompletionTimestampInvariant() {
        UUID owner = insertUser();

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO todos (id, owner_id, title, status, completed_at, created_at, updated_at) VALUES (?, ?, ?, 'COMPLETED', NULL, now(), now())",
                        UUID.randomUUID(),
                        owner,
                        "Completed without a timestamp"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO todos (id, owner_id, title, status, completed_at, created_at, updated_at) VALUES (?, ?, ?, 'PENDING', now(), now(), now())",
                        UUID.randomUUID(),
                        owner,
                        "Pending with a timestamp"))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbcTemplate.update(
                "INSERT INTO todos (id, owner_id, title, status, completed_at, created_at, updated_at) VALUES (?, ?, ?, 'PENDING', NULL, now(), now())",
                UUID.randomUUID(),
                owner,
                "Pending without a timestamp");
        jdbcTemplate.update(
                "INSERT INTO todos (id, owner_id, title, status, completed_at, created_at, updated_at) VALUES (?, ?, ?, 'COMPLETED', now(), now(), now())",
                UUID.randomUUID(),
                owner,
                "Completed with a timestamp");

        Integer todos = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM todos", Integer.class);
        assertThat(todos).isEqualTo(2);
    }

    /** The role is a closed set, so the database rejects a value the domain does not know. */
    @Test
    void enforcesTheRoleConstraint() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO users (id, email, password_hash, role, created_at) VALUES (?, ?, ?, 'SUPERUSER', now())",
                        UUID.randomUUID(),
                        "unknown-role-" + UUID.randomUUID() + "@example.com",
                        "not-a-real-hash"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Writes a user directly, so that the todos have an owner to reference. */
    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, role, created_at) VALUES (?, ?, ?, 'USER', now())",
                id,
                "flyway-" + id + "@example.com",
                "not-a-real-hash");
        return id;
    }
}
