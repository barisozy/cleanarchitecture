package com.barisozy.cleanarchitecture.web.error;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import com.barisozy.cleanarchitecture.application.ApplicationException;
import com.barisozy.cleanarchitecture.domain.DomainException;
import com.barisozy.cleanarchitecture.web.config.properties.ApiProperties;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;

/** Covers the exception-to-HTTP mapping of the shared web layer. */
class GlobalExceptionHandlerTest {

    private static final String URI = "/api/todos/7";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(
            new ProblemDetailFactory(new ApiProperties("https://example.test/problems", "http://localhost:8080")));

    @Test
    void mapsEveryApplicationExceptionCategory() {
        List<ApplicationException> exceptions = List.of(
                ApplicationException.validation("validation.failed", "Invalid."),
                ApplicationException.notFound("todo.not-found", "Todo not found."),
                ApplicationException.conflict("todo.already-completed", "Already completed."),
                ApplicationException.unauthorized("auth.invalid-credentials", "Invalid email or password."),
                ApplicationException.forbidden("security.forbidden", "Denied."));
        List<HttpStatus> statuses = List.of(
                HttpStatus.BAD_REQUEST,
                HttpStatus.NOT_FOUND,
                HttpStatus.CONFLICT,
                HttpStatus.UNAUTHORIZED,
                HttpStatus.FORBIDDEN);

        assertSoftly(softly -> {
            for (int index = 0; index < exceptions.size(); index++) {
                ApplicationException exception = exceptions.get(index);
                ProblemDetail problem = handler.handleApplication(exception, request());
                softly.assertThat(problem.getStatus())
                        .isEqualTo(statuses.get(index).value());
                softly.assertThat(problem.getTitle())
                        .isEqualTo(statuses.get(index).getReasonPhrase());
                softly.assertThat(problem.getDetail()).isEqualTo(exception.getMessage());
                softly.assertThat(problem.getInstance()).hasToString(URI);
                softly.assertThat(problem.getProperties()).containsEntry("code", exception.code());
            }
        });
    }

    @Test
    void mapsDomainFrameworkSecurityAndValidationFailures() throws Exception {
        ProblemDetail domain = handler.handleDomain(
                new DomainException("todo.completion-time.invalid", "Completion time must be set."), request());
        ProblemDetail authentication =
                handler.handleAuthentication(new BadCredentialsException("Bad credentials"), request());
        ProblemDetail accessDenied = handler.handleAccessDenied(new AccessDeniedException("Access denied"), request());

        Set<ConstraintViolation<?>> violations;
        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = validatorFactory.getValidator();
            violations = Set.copyOf(validator.validate(new PageParams(0)));
        }
        ProblemDetail constraintViolation =
                handler.handleConstraintViolation(new ConstraintViolationException(violations), request());

        MethodParameter parameter =
                new MethodParameter(ValidatedPayload.class.getDeclaredMethod("handle", Payload.class), 0);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Payload(), "payload");
        bindingResult.addError(new org.springframework.validation.FieldError(
                "payload", "email", "must be a well-formed email address"));
        ResponseEntity<Object> invalidBody = handler.handleMethodArgumentNotValid(
                new MethodArgumentNotValidException(parameter, bindingResult),
                new HttpHeaders(),
                HttpStatus.BAD_REQUEST,
                new ServletWebRequest(request()));
        ResponseEntity<Object> typeMismatch = handler.handleTypeMismatch(
                new TypeMismatchException("maybe", LocalDate.class),
                new HttpHeaders(),
                HttpStatusCode.valueOf(400),
                new ServletWebRequest(request()));

        assertSoftly(softly -> {
            softly.assertThat(domain.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT.value());
            softly.assertThat(domain.getProperties()).containsEntry("code", "todo.completion-time.invalid");
            softly.assertThat(authentication.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
            softly.assertThat(authentication.getProperties()).containsEntry("code", "security.unauthorized");
            softly.assertThat(accessDenied.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
            softly.assertThat(accessDenied.getProperties()).containsEntry("code", "security.forbidden");
            softly.assertThat(constraintViolation.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
            softly.assertThat(constraintViolation.getProperties()).containsEntry("code", "validation.failed");
            softly.assertThat(constraintViolation.getProperties().get("fieldErrors"))
                    .isInstanceOf(List.class);
        });
        assertThat((List<?>) constraintViolation.getProperties().get("fieldErrors"))
                .isNotEmpty();
        ProblemDetail bodyProblem = (ProblemDetail) requireNonNull(invalidBody.getBody());
        assertThat(bodyProblem.getProperties()).containsEntry("code", "validation.failed");
        assertThat(bodyProblem.getProperties().get("fieldErrors"))
                .isEqualTo(List.of(new FieldError("email", "must be a well-formed email address")));
        ProblemDetail mismatchProblem = (ProblemDetail) requireNonNull(typeMismatch.getBody());
        assertThat(mismatchProblem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(mismatchProblem.getProperties().get("fieldErrors")).isNotNull();
    }

    @Test
    void enrichesInheritedMvcProblemsWithTypeInstanceAndCode() {
        ResponseEntity<Object> response = handler.handleExceptionInternal(
                new HttpRequestMethodNotSupportedException("PATCH"),
                null,
                new HttpHeaders(),
                HttpStatus.METHOD_NOT_ALLOWED,
                new ServletWebRequest(request()));
        ProblemDetail existing =
                ProblemDetail.forStatusAndDetail(HttpStatus.METHOD_NOT_ALLOWED, "PATCH is not supported.");
        ResponseEntity<Object> preserved = handler.handleExceptionInternal(
                new HttpRequestMethodNotSupportedException("PATCH"),
                existing,
                new HttpHeaders(),
                HttpStatus.METHOD_NOT_ALLOWED,
                new ServletWebRequest(request()));

        ProblemDetail problem = (ProblemDetail) requireNonNull(response.getBody());
        ProblemDetail preservedProblem = (ProblemDetail) requireNonNull(preserved.getBody());
        assertSoftly(softly -> {
            softly.assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
            softly.assertThat(problem.getType())
                    .hasToString("https://example.test/problems/request.method-not-allowed");
            softly.assertThat(problem.getTitle()).isEqualTo("Method Not Allowed");
            softly.assertThat(problem.getInstance()).hasToString(URI);
            softly.assertThat(problem.getProperties()).containsEntry("code", "request.method-not-allowed");
            softly.assertThat(preservedProblem.getDetail()).isEqualTo("PATCH is not supported.");
        });
    }

    @Test
    void neverLeaksTheInternalMessageOfAnUnexpectedFailure() {
        ProblemDetail problem =
                handler.handleUnexpected(new IllegalStateException("connection pool exhausted"), request());

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getTitle()).isEqualTo("Internal Server Error");
        assertThat(problem.getDetail()).isEqualTo("An unexpected error occurred.");
        assertThat(problem.getProperties()).containsEntry("code", "internal.error");
        assertThat(problem.getProperties().values())
                .noneMatch(value -> String.valueOf(value).contains("connection pool"));
        assertThat(problem.getProperties().values())
                .noneMatch(value -> String.valueOf(value).contains("IllegalStateException"));
    }

    private static MockHttpServletRequest request() {
        return new MockHttpServletRequest("GET", URI);
    }

    /** A tiny bean whose only constraint stands for the validated query parameters. */
    private record PageParams(@Min(1) int size) {}

    /** The target of the method parameter whose body validation failed. */
    static class ValidatedPayload {
        void handle(Payload payload) {}
    }

    /** The validated body of a request. */
    static class Payload {
        public String getEmail() {
            return "user@example.com";
        }
    }
}
