package com.barisozy.cleanarchitecture.application;

/**
 * Stable categories used by the web layer to select an HTTP status code.
 *
 * <p>A domain invariant violation is deliberately not a category: a {@link
 * com.barisozy.cleanarchitecture.domain.DomainException} is mapped to 422 directly by the
 * exception handler, because "the request violates a business rule" is a different outcome from
 * "the request is malformed".
 */
public enum ErrorType {
    VALIDATION,
    NOT_FOUND,
    CONFLICT,
    UNAUTHORIZED,
    FORBIDDEN
}
