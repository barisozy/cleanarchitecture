package com.barisozy.cleanarchitecture.fixtures;

import com.barisozy.cleanarchitecture.domain.user.Email;
import com.barisozy.cleanarchitecture.domain.user.PasswordHash;
import com.barisozy.cleanarchitecture.domain.user.RoleName;
import com.barisozy.cleanarchitecture.domain.user.User;
import com.barisozy.cleanarchitecture.domain.user.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Deterministic user fixtures shared by the unit, web and integration tests.
 *
 * <p>The password hash is produced by a real BCrypt encoder with a low cost factor, so that the
 * fixture satisfies the format rule of the {@code PasswordHash} value object and can be verified
 * with the same encoder the application uses.
 */
public final class UserTestData {

    public static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");
    public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    public static final String USER_EMAIL = "user@example.com";
    public static final String ADMIN_EMAIL = "admin@example.com";
    public static final String RAW_PASSWORD = "StrongPassword123!";

    /** The identity of {@link #user()}, fixed so that assertions stay deterministic. */
    public static final UserId USER_ID = new UserId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

    /** The identity of {@link #admin()}. */
    public static final UserId ADMIN_ID = new UserId(UUID.fromString("22222222-2222-2222-2222-222222222222"));

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(4);

    private UserTestData() {}

    /** A regular user with a fixed identity, so that assertions stay deterministic. */
    public static User user() {
        return userWithEmail(USER_EMAIL, RoleName.USER);
    }

    /** An administrator with a fixed identity. */
    public static User admin() {
        return userWithEmail(ADMIN_EMAIL, RoleName.ADMIN);
    }

    public static User userWithEmail(String email) {
        return userWithEmail(email, RoleName.USER);
    }

    public static User userWithEmail(String email, RoleName role) {
        return new User(new UserId(UUID.randomUUID()), Email.of(email), passwordHash(), role, NOW);
    }

    /** A user with the fixed identity of {@link #USER_ID}. */
    public static User withFixedId() {
        return new User(USER_ID, Email.of(USER_EMAIL), passwordHash(), RoleName.USER, NOW);
    }

    /** A BCrypt hash of {@link #RAW_PASSWORD}. */
    public static PasswordHash passwordHash() {
        return PasswordHash.of(ENCODER.encode(RAW_PASSWORD));
    }
}
