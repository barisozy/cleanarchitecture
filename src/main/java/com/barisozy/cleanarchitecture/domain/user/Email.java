package com.barisozy.cleanarchitecture.domain.user;

import com.barisozy.cleanarchitecture.domain.DomainException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Normalized email address. */
public record Email(String value) {

    /** Pragmatic RFC 5322 subset: a dot-atom local part and a dotted domain name. */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*"
                    + "@[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$");

    private static final int MAX_LENGTH = 320;
    private static final int MAX_LOCAL_PART_LENGTH = 64;

    public Email {
        if (value == null) {
            throw new DomainException("user.email.invalid", "Email is required.");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        int at = normalized.indexOf('@');
        if (normalized.isEmpty()
                || normalized.length() > MAX_LENGTH
                || at < 0
                || at > MAX_LOCAL_PART_LENGTH
                || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new DomainException("user.email.invalid", "Email is not valid.");
        }
        value = normalized;
    }

    public static Email of(String value) {
        return new Email(value);
    }
}
