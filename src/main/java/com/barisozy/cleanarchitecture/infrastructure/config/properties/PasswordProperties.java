package com.barisozy.cleanarchitecture.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Password hashing settings.
 *
 * <p>The BCrypt cost factor applies to newly hashed passwords; the encoder still verifies hashes
 * with any cost, so raising the value does not invalidate existing accounts. Ten is the Spring
 * Security default and the deliberate baseline of the template; raise it only with a measured
 * login latency.
 */
@Validated
@ConfigurationProperties("app.security.password")
public record PasswordProperties(int bcryptStrength) {

    /** The lowest cost that is still considered a BCrypt cost. */
    public static final int MIN_STRENGTH = 4;

    /** The highest cost BCrypt supports. */
    public static final int MAX_STRENGTH = 31;

    public PasswordProperties {
        if (bcryptStrength < MIN_STRENGTH || bcryptStrength > MAX_STRENGTH) {
            throw new IllegalArgumentException("app.security.password.bcrypt-strength must be between " + MIN_STRENGTH
                    + " and " + MAX_STRENGTH + ".");
        }
    }
}
