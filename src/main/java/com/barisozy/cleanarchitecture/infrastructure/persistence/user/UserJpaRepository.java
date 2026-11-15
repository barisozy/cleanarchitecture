package com.barisozy.cleanarchitecture.infrastructure.persistence.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The Spring Data repository of the {@code users} table.
 *
 * <p>It stays package-private: only {@link UserPersistenceAdapter} may depend on it. Everything
 * else, including the development seeder, goes through the domain port.
 */
interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {

    Optional<UserJpaEntity> findByEmail(String email);
}
