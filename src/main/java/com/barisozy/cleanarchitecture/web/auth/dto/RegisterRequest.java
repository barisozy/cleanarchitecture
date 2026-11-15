package com.barisozy.cleanarchitecture.web.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The registration request.
 *
 * <p>The password has no maximum length here: the limit of the password algorithm is 72
 * <em>bytes</em>, which cannot be expressed with a character count, and the domain enforces it.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 12) String password) {

    /** The password is a credential, so it never appears in a log line or an error report. */
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", password=********]";
    }
}
