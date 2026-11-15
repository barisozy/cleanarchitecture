package com.barisozy.cleanarchitecture.domain.user;

import com.barisozy.cleanarchitecture.domain.DomainException;
import java.nio.charset.StandardCharsets;

/**
 * A raw password. It never reaches persistence and it is never rendered.
 *
 * <p>The upper bound is 72 <em>bytes</em>, not characters: BCrypt hashes at most 72 bytes of the
 * input and silently truncates everything beyond. Spring Security's {@code matches} keeps that
 * truncation for backwards compatibility, so a password of 73 bytes that shares its first 72 bytes
 * with the stored hash would be accepted. Rejecting the input at the type level makes that
 * truncation unreachable.
 *
 * <p>The lower bound only applies to registration. Login deliberately accepts shorter passwords so
 * that accounts created before the rule existed keep working.
 */
public record RawPassword(String value) {

    /** Minimum length in characters, enforced when a password is registered. */
    public static final int MIN_LENGTH = 12;

    /** Maximum length in UTF-8 bytes; this is the BCrypt limit. */
    public static final int MAX_BYTES = 72;

    public RawPassword {
        if (value == null || value.isEmpty()) {
            throw new DomainException("user.password.invalid", "Password is required.");
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new DomainException("user.password.too-long", "Password must not exceed " + MAX_BYTES + " bytes.");
        }
    }

    /** A password that is about to be registered: the length rule applies in full. */
    public static RawPassword forRegistration(String value) {
        RawPassword password = new RawPassword(value);
        if (password.value().length() < MIN_LENGTH) {
            throw new DomainException(
                    "user.password.invalid", "Password must be at least " + MIN_LENGTH + " characters.");
        }
        return password;
    }

    /** A password that is presented to the login flow: only the BCrypt byte limit applies. */
    public static RawPassword forLogin(String value) {
        return new RawPassword(value);
    }

    @Override
    public String toString() {
        return "RawPassword[********]";
    }
}
