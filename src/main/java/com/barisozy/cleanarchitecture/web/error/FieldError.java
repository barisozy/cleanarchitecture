package com.barisozy.cleanarchitecture.web.error;

/**
 * A single field-level validation failure.
 *
 * <p>The name of the field and a human readable message are the only two things a client needs to
 * point at the offending input.
 */
public record FieldError(String field, String message) {}
