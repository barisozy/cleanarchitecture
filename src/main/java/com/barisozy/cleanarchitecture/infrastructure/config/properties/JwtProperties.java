package com.barisozy.cleanarchitecture.infrastructure.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Base64;
import org.hibernate.validator.constraints.time.DurationMin;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Signing and token settings.
 *
 * <p>The secret is a Base64 encoded value of at least {@value #MIN_SECRET_BYTES} bytes, the key
 * size HS256 expects. The local profile generates an ephemeral key instead; every other profile
 * requires the secret and fails at startup without one.
 */
@Validated
@ConfigurationProperties("app.security.jwt")
public record JwtProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotNull @DurationMin(seconds = 1) Duration accessTokenTtl,
        @Nullable String secret) {

    /** The minimum key size of HS256, in bytes. */
    public static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        requireSupplied("issuer", issuer);
        requireSupplied("audience", audience);
        if (secret != null && !secret.isBlank()) {
            if (decode(secret).length < MIN_SECRET_BYTES) {
                throw new IllegalArgumentException(
                        "app.security.jwt.secret must decode to at least " + MIN_SECRET_BYTES + " bytes.");
            }
        } else {
            secret = null;
        }
    }

    /**
     * The binder leaves a placeholder it cannot resolve untouched, so a missing environment
     * variable would otherwise bind the literal {@code ${JWT_ISSUER}} and pass a blankness check.
     */
    private static void requireSupplied(String property, @Nullable String value) {
        if (value != null && value.contains("${")) {
            throw new IllegalArgumentException(
                    "app.security.jwt." + property + " must be supplied by the environment.");
        }
    }

    private static byte[] decode(String secret) {
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("app.security.jwt.secret must be Base64 encoded.", exception);
        }
    }
}
