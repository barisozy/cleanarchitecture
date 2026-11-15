package com.barisozy.cleanarchitecture.web.error;

import com.barisozy.cleanarchitecture.application.ApplicationException;
import com.barisozy.cleanarchitecture.application.ErrorType;
import com.barisozy.cleanarchitecture.domain.DomainException;
import com.barisozy.cleanarchitecture.web.config.properties.ApiProperties;
import jakarta.validation.ConstraintViolation;
import java.net.URI;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Central factory for RFC 9457 responses. It is shared by the exception handler and the servlet
 * filters so that every error response carries the same envelope.
 *
 * <p>Every response carries the machine-readable {@code code}, the {@code correlationId} of the
 * request and the {@code instance} that identifies the failed request. Stack traces and internal
 * messages never appear in the body.
 */
@Component
public class ProblemDetailFactory {

    private final ApiProperties properties;

    public ProblemDetailFactory(ApiProperties properties) {
        this.properties = properties;
    }

    public ProblemDetail create(HttpStatus status, String code, String detail, String instance) {
        return enrich(ProblemDetail.forStatusAndDetail(status, detail), status, code, instance);
    }

    /**
     * Applies the envelope of the API to a problem that Spring MVC created, preserving its detail.
     * The inherited handlers of {@code ResponseEntityExceptionHandler} use this so that their
     * responses carry the same type, code and correlation id as the ones the application raises.
     */
    public ProblemDetail enrich(ProblemDetail problem, HttpStatus status, String code, String instance) {
        problem.setTitle(status.getReasonPhrase());
        problem.setType(problemType(code));
        if (instance != null && !instance.isBlank()) {
            problem.setInstance(URI.create(instance));
        }
        problem.setProperty("code", code);
        problem.setProperty("correlationId", correlationId());
        return problem;
    }

    /** An expected business outcome; the status follows from the error category. */
    public ProblemDetail from(ApplicationException exception, String instance) {
        HttpStatus status = statusFor(exception.type());
        return create(status, exception.code(), exception.message(), instance);
    }

    /** A violated domain invariant: the request is well-formed but breaks a business rule. */
    public ProblemDetail unprocessable(DomainException exception, String instance) {
        return create(HttpStatus.UNPROCESSABLE_CONTENT, exception.code(), exception.message(), instance);
    }

    public ProblemDetail unauthorized(String code, String instance) {
        return create(HttpStatus.UNAUTHORIZED, code, "Authentication is required.", instance);
    }

    public ProblemDetail forbidden(String code, String instance) {
        return create(HttpStatus.FORBIDDEN, code, "You do not have permission to perform this action.", instance);
    }

    /** A validation failure of the request body or of the request parameters. */
    public ProblemDetail validation(List<FieldError> fieldErrors, String instance) {
        ProblemDetail problem =
                create(HttpStatus.BAD_REQUEST, "validation.failed", "Request validation failed.", instance);
        if (!fieldErrors.isEmpty()) {
            problem.setProperty("fieldErrors", List.copyOf(fieldErrors));
        }
        return problem;
    }

    /** A validation failure of a method parameter that is annotated with a constraint. */
    public ProblemDetail validation(Set<ConstraintViolation<?>> violations, String instance) {
        List<FieldError> fieldErrors = violations.stream()
                .map(violation -> new FieldError(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return validation(fieldErrors, instance);
    }

    /** An unexpected failure; the cause is logged but never exposed. */
    public ProblemDetail internal(String instance) {
        return create(HttpStatus.INTERNAL_SERVER_ERROR, "internal.error", "An unexpected error occurred.", instance);
    }

    /** The correlation id of the current request, or {@code null} outside of a request. */
    public @Nullable String correlationId() {
        return MDC.get(CorrelationIdFilter.MDC_KEY);
    }

    /** Maps an application error category to the HTTP status code that represents it. */
    public static HttpStatus statusFor(ErrorType type) {
        return switch (type) {
            case VALIDATION -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
        };
    }

    /** The machine-readable code of a failure that has no business code of its own. */
    public static String codeFor(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "validation.failed";
            case NOT_FOUND -> "resource.not-found";
            case METHOD_NOT_ALLOWED -> "request.method-not-allowed";
            case UNSUPPORTED_MEDIA_TYPE -> "request.media-type-not-supported";
            default -> "request.failed";
        };
    }

    private URI problemType(String code) {
        String base = properties.problemBaseUri();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return URI.create(base + "/" + code);
    }
}
