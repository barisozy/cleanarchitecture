package com.barisozy.cleanarchitecture.web.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * CORS policy of the browser-facing API.
 *
 * <p>Origins are listed explicitly; a wildcard is never combined with credentials, because browsers
 * reject that combination anyway.
 */
@Validated
@ConfigurationProperties("app.security.cors")
public record CorsProperties(
        @NotEmpty List<@NotBlank String> allowedOrigins,
        boolean allowCredentials,
        @NotNull @DurationMin(seconds = 1) Duration maxAge) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        if (allowCredentials && allowedOrigins.contains("*")) {
            throw new IllegalArgumentException(
                    "A wildcard CORS origin cannot be combined with credentials; list the allowed origins explicitly.");
        }
        // The binder leaves an unresolved placeholder untouched, so a missing environment variable
        // would otherwise become the literal origin "${CORS_ALLOWED_ORIGINS}".
        if (allowedOrigins.stream().anyMatch(origin -> origin.contains("${"))) {
            throw new IllegalArgumentException(
                    "app.security.cors.allowed-origins must be supplied by the environment.");
        }
    }
}
