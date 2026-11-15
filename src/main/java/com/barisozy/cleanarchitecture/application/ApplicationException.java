package com.barisozy.cleanarchitecture.application;

import static java.util.Objects.requireNonNull;

/**
 * An expected business outcome that the caller has to handle explicitly.
 *
 * <p>This is the Java ecosystem standard for reporting a rejected but legitimate request: the
 * caller does not get a value, it gets an exception that carries the machine-readable contract of
 * the failure. The web layer translates the exception into an RFC 9457 {@code ProblemDetail}; the
 * exception itself stays free of HTTP concepts.
 *
 * <p>The distinction is deliberate:
 *
 * <ul>
 *   <li>An {@code ApplicationException} is part of the business contract — "the e-mail is already
 *       registered", "the todo does not exist", "the credentials are wrong". It is thrown on
 *       purpose, it carries a stable {@link #code()} and it never leaks internals.
 *   <li>A {@link com.barisozy.cleanarchitecture.domain.DomainException} signals a violated
 *       business rule. It is mapped to {@code 422}, because the request is well-formed but breaks
 *       an invariant.
 *   <li>Any other runtime exception is an unexpected failure and is reported as a 500.
 * </ul>
 *
 * <p>Use the static factories instead of the constructor so that the {@link ErrorType} — and with it
 * the HTTP status — always matches the semantics of the failure.
 */
public final class ApplicationException extends RuntimeException {

    private final String code;
    private final ErrorType type;

    private ApplicationException(String code, String message, ErrorType type) {
        super(requireNonNull(message, "message"));
        this.code = requireNonNull(code, "code");
        this.type = requireNonNull(type, "type");
    }

    /** The request contains a value that the business rules reject. */
    public static ApplicationException validation(String code, String message) {
        return new ApplicationException(code, message, ErrorType.VALIDATION);
    }

    /** The requested resource does not exist or must not be disclosed. */
    public static ApplicationException notFound(String code, String message) {
        return new ApplicationException(code, message, ErrorType.NOT_FOUND);
    }

    /** The request conflicts with the current state of the resource. */
    public static ApplicationException conflict(String code, String message) {
        return new ApplicationException(code, message, ErrorType.CONFLICT);
    }

    /** The caller could not be authenticated. */
    public static ApplicationException unauthorized(String code, String message) {
        return new ApplicationException(code, message, ErrorType.UNAUTHORIZED);
    }

    /** The caller is authenticated but lacks the required permission. */
    public static ApplicationException forbidden(String code, String message) {
        return new ApplicationException(code, message, ErrorType.FORBIDDEN);
    }

    /** The stable, machine-readable error code, for example {@code todo.not-found}. */
    public String code() {
        return code;
    }

    /**
     * The message of the rejected request. The constructor requires a message, so it is never
     * {@code null}; the accessor exists because {@link Throwable#getMessage()} is nullable.
     */
    public String message() {
        return requireNonNull(getMessage(), "message");
    }

    /** The error category; the web layer maps it to an HTTP status code. */
    public ErrorType type() {
        return type;
    }
}
