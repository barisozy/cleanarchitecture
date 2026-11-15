package com.barisozy.cleanarchitecture.web.error;

import com.barisozy.cleanarchitecture.application.ApplicationException;
import com.barisozy.cleanarchitecture.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The single place where an exception becomes an HTTP response.
 *
 * <p>The advice extends {@link ResponseEntityExceptionHandler}, so that the exceptions Spring MVC
 * raises on its own — a malformed body, a type mismatch, a missing parameter — are rendered with
 * the same envelope as the exceptions the application raises. The overrides exist to add the
 * {@code code} and {@code fieldErrors} properties; without them the body of Spring's default
 * response would not match the contract of this API.
 *
 * <p>The advice runs with the highest precedence, so that its contract is the one that is
 * observed. The order of the advice is compared before the specificity of the exception type, and
 * the specificity is only compared between the handlers of a single advice.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ProblemDetailFactory problems;

    public GlobalExceptionHandler(ProblemDetailFactory problems) {
        this.problems = problems;
    }

    /** Expected business outcomes: the service throws them on purpose. */
    @ExceptionHandler(ApplicationException.class)
    ProblemDetail handleApplication(ApplicationException exception, HttpServletRequest request) {
        return problems.from(exception, request.getRequestURI());
    }

    /** A violated domain invariant. */
    @ExceptionHandler(DomainException.class)
    ProblemDetail handleDomain(DomainException exception, HttpServletRequest request) {
        return problems.unprocessable(exception, request.getRequestURI());
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleAuthentication(AuthenticationException exception, HttpServletRequest request) {
        return problems.unauthorized("security.unauthorized", request.getRequestURI());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return problems.forbidden("security.forbidden", request.getRequestURI());
    }

    /** A constraint that was violated on a query or path parameter. */
    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        return problems.validation(exception.getConstraintViolations(), request.getRequestURI());
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<FieldError> errors = new ArrayList<>();
        for (ParameterValidationResult result : exception.getParameterValidationResults()) {
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                errors.add(new FieldError(
                        result.getMethodParameter().getParameterName(),
                        error.getDefaultMessage() == null ? "Invalid value." : error.getDefaultMessage()));
            }
        }
        return validationResponse(errors, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldError> errors = new ArrayList<>();
        for (org.springframework.validation.FieldError fieldError :
                exception.getBindingResult().getFieldErrors()) {
            errors.add(new FieldError(
                    fieldError.getField(),
                    fieldError.getDefaultMessage() == null ? "Invalid value." : fieldError.getDefaultMessage()));
        }
        return validationResponse(errors, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String field = exception.getPropertyName() == null ? "request" : exception.getPropertyName();
        List<FieldError> errors = List.of(new FieldError(field, "The value has the wrong type."));
        return validationResponse(errors, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return validationResponse(List.of(), headers, status, request);
    }

    /**
     * Enriches the problems that Spring MVC raises on its own.
     *
     * <p>Without this the responses of the inherited handlers would carry neither the RFC 9457
     * type and instance, nor the error code and the correlation id, and the client would see two
     * different error envelopes.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        HttpStatus httpStatus = resolved == null ? HttpStatus.INTERNAL_SERVER_ERROR : resolved;
        ProblemDetail problem = body instanceof ProblemDetail detail
                ? detail
                : ProblemDetail.forStatusAndDetail(httpStatus, httpStatus.getReasonPhrase());
        problems.enrich(problem, httpStatus, ProblemDetailFactory.codeFor(httpStatus), instanceOf(request));
        return createResponseEntity(problem, headers, status, request);
    }

    /** Anything that is not an expected outcome is a bug; it is logged and reported as a 500. */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception, correlationId={}", problems.correlationId(), exception);
        return problems.internal(request.getRequestURI());
    }

    private ResponseEntity<Object> validationResponse(
            List<FieldError> errors, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problems.validation(errors, instanceOf(request));
        return createResponseEntity(problem, headers, status, request);
    }

    /** The path of the failed request; the instance of the problem identifies it. */
    private static String instanceOf(WebRequest request) {
        if (request instanceof NativeWebRequest nativeRequest) {
            HttpServletRequest servletRequest = nativeRequest.getNativeRequest(HttpServletRequest.class);
            if (servletRequest != null) {
                return servletRequest.getRequestURI();
            }
        }
        return "";
    }
}
