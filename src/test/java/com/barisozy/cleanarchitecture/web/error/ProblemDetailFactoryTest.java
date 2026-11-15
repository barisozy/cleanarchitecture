package com.barisozy.cleanarchitecture.web.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import com.barisozy.cleanarchitecture.application.ApplicationException;
import com.barisozy.cleanarchitecture.application.ErrorType;
import com.barisozy.cleanarchitecture.domain.DomainException;
import com.barisozy.cleanarchitecture.web.config.properties.ApiProperties;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Covers the status mapping and the RFC 9457 envelope of the shared error factory. */
class ProblemDetailFactoryTest {

    private static final String INSTANCE = "/api/todos";

    private final ProblemDetailFactory factory =
            new ProblemDetailFactory(new ApiProperties("https://example.test/problems/", "http://localhost:8080"));

    @AfterEach
    void clearCorrelationId() {
        MDC.clear();
    }

    @Test
    void mapsEveryErrorTypeToItsStatus() {
        assertThat(ProblemDetailFactory.statusFor(ErrorType.VALIDATION)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ProblemDetailFactory.statusFor(ErrorType.NOT_FOUND)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ProblemDetailFactory.statusFor(ErrorType.CONFLICT)).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ProblemDetailFactory.statusFor(ErrorType.UNAUTHORIZED)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ProblemDetailFactory.statusFor(ErrorType.FORBIDDEN)).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void buildsTheRfc9457Envelope() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "correlation-7");

        ProblemDetail problem = factory.create(HttpStatus.BAD_REQUEST, "auth.invalid_credentials", "Nope.", INSTANCE);

        assertThat(problem.getType()).hasToString("https://example.test/problems/auth.invalid_credentials");
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Bad Request");
        assertThat(problem.getDetail()).isEqualTo("Nope.");
        assertThat(problem.getInstance()).hasToString(INSTANCE);
        assertThat(problem.getProperties())
                .containsEntry("code", "auth.invalid_credentials")
                .containsEntry("correlationId", "correlation-7")
                .doesNotContainKey("fieldErrors");
        assertThat(factory.correlationId()).isEqualTo("correlation-7");

        ProblemDetail withoutOptionalValues =
                factory.create(HttpStatus.INTERNAL_SERVER_ERROR, "internal.error", "Oops.", "");
        assertThat(withoutOptionalValues.getInstance()).isNull();
    }

    @Test
    void mapsFailuresAndFrameworkStatusesToTheirProblemContract() {
        assertSoftly(softly -> {
            ProblemDetail application =
                    factory.from(ApplicationException.notFound("todo.not-found", "Todo not found."), INSTANCE);
            softly.assertThat(application.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
            softly.assertThat(application.getProperties()).containsEntry("code", "todo.not-found");

            ProblemDetail domain = factory.unprocessable(
                    new DomainException("todo.completion-time.invalid", "Completion time must be set."), INSTANCE);
            softly.assertThat(domain.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT.value());
            softly.assertThat(domain.getProperties()).containsEntry("code", "todo.completion-time.invalid");

            ProblemDetail unauthorized = factory.unauthorized("security.unauthorized", INSTANCE);
            softly.assertThat(unauthorized.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
            ProblemDetail forbidden = factory.forbidden("security.forbidden", INSTANCE);
            softly.assertThat(forbidden.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());

            List<FieldError> fieldErrors = List.of(new FieldError("title", "must not be blank"));
            ProblemDetail validation = factory.validation(fieldErrors, INSTANCE);
            softly.assertThat(validation.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
            softly.assertThat(validation.getProperties()).containsEntry("fieldErrors", fieldErrors);

            ProblemDetail internal = factory.internal(INSTANCE);
            softly.assertThat(internal.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
            softly.assertThat(internal.getDetail()).isEqualTo("An unexpected error occurred.");

            softly.assertThat(ProblemDetailFactory.codeFor(HttpStatus.BAD_REQUEST))
                    .isEqualTo("validation.failed");
            softly.assertThat(ProblemDetailFactory.codeFor(HttpStatus.NOT_FOUND))
                    .isEqualTo("resource.not-found");
            softly.assertThat(ProblemDetailFactory.codeFor(HttpStatus.METHOD_NOT_ALLOWED))
                    .isEqualTo("request.method-not-allowed");
            softly.assertThat(ProblemDetailFactory.codeFor(HttpStatus.UNSUPPORTED_MEDIA_TYPE))
                    .isEqualTo("request.media-type-not-supported");
            softly.assertThat(ProblemDetailFactory.codeFor(HttpStatus.INTERNAL_SERVER_ERROR))
                    .isEqualTo("request.failed");
        });

        Set<ConstraintViolation<?>> violations;
        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = validatorFactory.getValidator();
            violations = Set.copyOf(validator.validate(new PageParams(0)));
        }
        ProblemDetail validation = factory.validation(violations, INSTANCE);
        assertThat(validation.getProperties().get("fieldErrors")).isInstanceOf(List.class);
        assertThat((List<?>) validation.getProperties().get("fieldErrors")).hasSize(1);
    }

    /** A tiny bean whose only constraint stands for the validated query parameters. */
    private record PageParams(@Min(1) int size) {}
}
