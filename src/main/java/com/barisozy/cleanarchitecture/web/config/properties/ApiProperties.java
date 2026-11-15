package com.barisozy.cleanarchitecture.web.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for the shared web contract.
 *
 * @param problemBaseUri base URI of the problem type documentation
 * @param publicBaseUrl externally reachable base URL of the API; it is published in the OpenAPI
 *     document
 */
@Validated
@ConfigurationProperties("app.api")
public record ApiProperties(
        @NotBlank @URL @Pattern(regexp = "https?://\\S+", message = "must be an http or https URL") String problemBaseUri,

        @NotBlank @URL @Pattern(regexp = "https?://\\S+", message = "must be an http or https URL") String publicBaseUrl) {

    /**
     * The binder leaves a placeholder it cannot resolve untouched, so a missing environment
     * variable would otherwise bind the literal {@code ${PROBLEM_BASE_URI}} and fail the URL check
     * with a message about the literal instead of the missing value. Reject it as a missing value.
     */
    public ApiProperties {
        requireSupplied("app.api.problem-base-uri", problemBaseUri);
        requireSupplied("app.api.public-base-url", publicBaseUrl);
    }

    private static void requireSupplied(String property, String value) {
        if (value != null && value.contains("${")) {
            throw new IllegalArgumentException(property + " must be supplied by the environment.");
        }
    }
}
