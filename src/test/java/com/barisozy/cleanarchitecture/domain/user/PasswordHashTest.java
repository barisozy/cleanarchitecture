package com.barisozy.cleanarchitecture.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.barisozy.cleanarchitecture.domain.DomainException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class PasswordHashTest {

    /** A real BCrypt hash: {@code $2a$10$} prefix plus 53 salt and hash characters. */
    private static final String BCRYPT_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    /** The 53 characters that follow the {@code $2<variant>$<cost>$} prefix of a BCrypt hash. */
    private static final String SALT_AND_HASH = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0";

    @Test
    void acceptsOnlyRealBcryptHashes() {
        assertThat(PasswordHash.of(BCRYPT_HASH).value()).isEqualTo(BCRYPT_HASH);
        assertThat(PasswordHash.of("$2b$12$" + SALT_AND_HASH).value()).isEqualTo("$2b$12$" + SALT_AND_HASH);
        assertThat(PasswordHash.of("$2y$05$" + SALT_AND_HASH).value()).isEqualTo("$2y$05$" + SALT_AND_HASH);
        assertThat(PasswordHash.of("$2a$31$" + SALT_AND_HASH).value()).isEqualTo("$2a$31$" + SALT_AND_HASH);

        assertRejected("plain", "Unsupported password hash format.");
        assertRejected("$2d$10$" + SALT_AND_HASH, "Unsupported password hash format.");
        assertRejected("$2a$10$tooshort", "Unsupported password hash format.");
        assertRejected("$2c$10$" + SALT_AND_HASH, "Unsupported password hash format.");
        assertRejected("$2a$00$" + SALT_AND_HASH, "Unsupported password hash format.");
        assertRejected("$2a$03$" + SALT_AND_HASH, "Unsupported password hash format.");
        assertRejected("$2a$99$" + SALT_AND_HASH, "Unsupported password hash format.");
        assertRejected("   ", "Password hash is required.");
        assertRejected(null, "Password hash is required.");
    }

    @Test
    void isRedactedInToString() {
        PasswordHash hash = PasswordHash.of(BCRYPT_HASH);

        assertThat(hash).hasToString("PasswordHash[********]");
        assertThat(hash.toString()).doesNotContain(BCRYPT_HASH);
    }

    private static void assertRejected(@Nullable String value, String message) {
        assertThatThrownBy(() -> PasswordHash.of(value))
                .isInstanceOf(DomainException.class)
                .satisfies(exception ->
                        assertThat(((DomainException) exception).code()).isEqualTo("user.password-hash.invalid"))
                .hasMessage(message);
    }
}
