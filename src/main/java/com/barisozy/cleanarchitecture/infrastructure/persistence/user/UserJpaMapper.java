package com.barisozy.cleanarchitecture.infrastructure.persistence.user;

import com.barisozy.cleanarchitecture.domain.user.Email;
import com.barisozy.cleanarchitecture.domain.user.PasswordHash;
import com.barisozy.cleanarchitecture.domain.user.User;
import com.barisozy.cleanarchitecture.domain.user.UserId;
import org.springframework.stereotype.Component;

/**
 * Maps between the user aggregate and its persistence representation.
 *
 * <p>The mapper translates values; it never re-applies a business rule. The only validation it
 * performs is the format check that the value objects carry.
 */
@Component
public class UserJpaMapper {

    public User toDomain(UserJpaEntity entity) {
        return new User(
                new UserId(entity.getId()),
                new Email(entity.getEmail()),
                new PasswordHash(entity.getPasswordHash()),
                entity.getRole(),
                entity.getCreatedAt());
    }

    public UserJpaEntity toEntity(User user) {
        return new UserJpaEntity(
                user.id().value(), user.email().value(), user.passwordHash().value(), user.role(), user.createdAt());
    }
}
