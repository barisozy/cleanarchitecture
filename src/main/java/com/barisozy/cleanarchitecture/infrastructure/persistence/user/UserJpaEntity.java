package com.barisozy.cleanarchitecture.infrastructure.persistence.user;

import com.barisozy.cleanarchitecture.domain.user.RoleName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * The persistence representation of a user.
 *
 * <p>The entity is a storage model, not the domain model: it mirrors the table, it carries no
 * behaviour and it is never handed to the application layer. The user is insert-only, so the row
 * has no version and no mutable state.
 *
 * <p>The identifier is assigned by the domain, which makes Spring Data treat the entity as
 * existing and issue a merge. {@link Persistable} overrides that decision, so a registration is a
 * single insert instead of a select followed by an insert.
 */
@Entity
@Table(name = "users")
public class UserJpaEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew = true;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoleName role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserJpaEntity() {}

    public UserJpaEntity(UUID id, String email, String passwordHash, RoleName role, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = createdAt;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public RoleName getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
