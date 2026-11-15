package com.barisozy.cleanarchitecture.web.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * The login request.
 *
 * <p>The request carries no format rule beyond "not blank": an address that is not well formed and
 * a password that is too long are both rejected with the same 401, so that the login endpoint
 * cannot be used to probe for valid addresses.
 */
public record LoginRequest(@NotBlank String email, @NotBlank String password) {

    /** The password is a credential, so it never appears in a log line or an error report. */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=********]";
    }
}
