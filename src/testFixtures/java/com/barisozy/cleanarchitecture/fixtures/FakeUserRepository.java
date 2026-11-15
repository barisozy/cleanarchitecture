package com.barisozy.cleanarchitecture.fixtures;

import com.barisozy.cleanarchitecture.domain.user.Email;
import com.barisozy.cleanarchitecture.domain.user.User;
import com.barisozy.cleanarchitecture.domain.user.UserId;
import com.barisozy.cleanarchitecture.domain.user.UserRepository;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory user repository for the application unit tests.
 *
 * <p>The fake only stores and reads. It applies no business rule and reads no clock: the rules are
 * verified in the domain tests.
 */
public final class FakeUserRepository implements UserRepository {

    private final Map<UserId, User> users = new LinkedHashMap<>();

    public FakeUserRepository(User... initialUsers) {
        for (User user : initialUsers) {
            users.put(user.id(), user);
        }
    }

    @Override
    public Optional<User> findById(UserId id) {
        return Optional.ofNullable(users.get(id));
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return users.values().stream()
                .filter(user -> user.email().equals(email))
                .findFirst();
    }

    @Override
    public User insert(User user) {
        users.put(user.id(), user);
        return user;
    }

    /** The stored users; the insertion order is preserved so that assertions stay deterministic. */
    public Map<UserId, User> users() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(users));
    }
}
