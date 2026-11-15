package com.barisozy.cleanarchitecture.infrastructure.persistence.user;

import com.barisozy.cleanarchitecture.application.ApplicationException;
import com.barisozy.cleanarchitecture.domain.user.Email;
import com.barisozy.cleanarchitecture.domain.user.User;
import com.barisozy.cleanarchitecture.domain.user.UserId;
import com.barisozy.cleanarchitecture.domain.user.UserRepository;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * The persistence adapter of the {@link User} aggregate.
 *
 * <p>The adapter joins the transaction of its caller; it never starts one. The write is flushed
 * eagerly so that a unique constraint violation surfaces here, where it can be translated, instead
 * of at the transaction boundary, where it would be an opaque error.
 *
 * <p>Only the known unique constraint is translated into a business conflict. Every other
 * integrity violation keeps its original type, so that a broken foreign key is never mistaken for
 * a duplicate registration.
 */
@Component
public class UserPersistenceAdapter implements UserRepository {

    /** The unique constraint on {@code users.email}, declared by {@code V1__init.sql}. */
    private static final String EMAIL_UNIQUE_CONSTRAINT = "uq_users_email";

    private final UserJpaRepository repository;
    private final UserJpaMapper mapper;

    public UserPersistenceAdapter(UserJpaRepository repository, UserJpaMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<User> findById(UserId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return repository.findByEmail(email.value()).map(mapper::toDomain);
    }

    @Override
    public User insert(User user) {
        UserJpaEntity entity = mapper.toEntity(user);
        try {
            repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateEmail(exception)) {
                throw ApplicationException.conflict(
                        "auth.email-already-registered", "An account with this email already exists.");
            }
            throw exception;
        }
        return mapper.toDomain(entity);
    }

    /** Reports whether the failure is the unique constraint on {@code users.email}. */
    private static boolean isDuplicateEmail(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return EMAIL_UNIQUE_CONSTRAINT.equals(violation.getConstraintName());
            }
        }
        return false;
    }
}
