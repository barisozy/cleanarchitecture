package com.barisozy.cleanarchitecture.domain;

import static java.util.Objects.requireNonNull;

/**
 * Signals that a domain invariant has been violated. Domain exceptions describe programming or
 * input errors that callers cannot normally recover from without changing the request.
 */
public class DomainException extends RuntimeException {

    private final String code;

    public DomainException(String code, String message) {
        super(requireNonNull(message, "message"));
        this.code = requireNonNull(code, "code");
    }

    public DomainException(String code, String message, Throwable cause) {
        super(requireNonNull(message, "message"), requireNonNull(cause, "cause"));
        this.code = requireNonNull(code, "code");
    }

    public String code() {
        return code;
    }

    /**
     * The message of the violated rule. The constructor requires a message, so it is never {@code
     * null}; the accessor exists because {@link Throwable#getMessage()} is nullable.
     */
    public String message() {
        return requireNonNull(getMessage(), "message");
    }
}
