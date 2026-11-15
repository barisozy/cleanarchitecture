package com.barisozy.cleanarchitecture.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.barisozy.cleanarchitecture.fixtures.UserTestData;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final Instant NOW = UserTestData.NOW;

    @Test
    void registerKeepsTheGivenValuesAndCreatesAFreshIdentity() {
        Email email = email();
        PasswordHash passwordHash = passwordHash();

        User user = User.register(email, passwordHash, RoleName.ADMIN, NOW);

        assertThat(user.id()).isNotNull();
        assertThat(user.id().value()).isNotNull();
        assertThat(user.id())
                .isNotEqualTo(
                        User.register(email, passwordHash, RoleName.ADMIN, NOW).id());
        assertThat(user.email()).isEqualTo(email);
        assertThat(user.passwordHash()).isEqualTo(passwordHash);
        assertThat(user.role()).isEqualTo(RoleName.ADMIN);
        assertThat(user.createdAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsMissingValues() {
        assertRejected(null, email(), passwordHash(), RoleName.USER, NOW);
        assertRejected(userId(), null, passwordHash(), RoleName.USER, NOW);
        assertRejected(userId(), email(), null, RoleName.USER, NOW);
        assertRejected(userId(), email(), passwordHash(), null, NOW);
        assertRejected(userId(), email(), passwordHash(), RoleName.USER, null);
    }

    private static void assertRejected(
            @Nullable UserId id,
            @Nullable Email email,
            @Nullable PasswordHash passwordHash,
            @Nullable RoleName role,
            @Nullable Instant createdAt) {
        assertThatThrownBy(() -> new User(id, email, passwordHash, role, createdAt))
                .isInstanceOf(NullPointerException.class);
    }

    private static Email email() {
        return Email.of("user@example.com");
    }

    private static PasswordHash passwordHash() {
        return UserTestData.passwordHash();
    }

    private static UserId userId() {
        return UserId.newId();
    }
}
