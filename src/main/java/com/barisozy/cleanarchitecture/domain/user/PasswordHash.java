package com.barisozy.cleanarchitecture.domain.user;

import com.barisozy.cleanarchitecture.domain.DomainException;
import java.util.regex.Pattern;

/**
 * An encoded password.
 *
 * <p>Only BCrypt hashes are accepted. The format check makes sure that a value that claims to be a
 * password hash really is one, so that a misconfigured encoder cannot silently store a reversible
 * value.
 */
public record PasswordHash(String value) {

    /** {@code $2<variant>$<cost>$<22 character salt><31 character hash>}; cost is 04–31. */
    private static final Pattern BCRYPT = Pattern.compile("^\\$2[aby]\\$(0[4-9]|[12][0-9]|3[01])\\$[./A-Za-z0-9]{53}$");

    public PasswordHash {
        if (value == null || value.isBlank()) {
            throw new DomainException("user.password-hash.invalid", "Password hash is required.");
        }
        if (!BCRYPT.matcher(value).matches()) {
            throw new DomainException("user.password-hash.invalid", "Unsupported password hash format.");
        }
    }

    /** Builds a hash from an encoded value, for example the result of the password encoder. */
    public static PasswordHash of(String value) {
        return new PasswordHash(value);
    }

    @Override
    public String toString() {
        return "PasswordHash[********]";
    }
}
