package com.barisozy.cleanarchitecture.web.auth.dto;

import com.barisozy.cleanarchitecture.application.auth.AuthResult;

/**
 * The response of a successful registration or login.
 *
 * <p>The access token is the only credential the API issues. It travels in the {@code Authorization}
 * header of the following requests and never in a cookie.
 */
public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {

    public static AuthResponse from(AuthResult result) {
        return new AuthResponse(
                result.accessToken(), result.tokenType(), result.expiresIn(), UserResponse.from(result.user()));
    }

    /** The access token is a credential, so it never appears in a log line or an error report. */
    @Override
    public String toString() {
        return "AuthResponse[accessToken=********, tokenType=" + tokenType + ", expiresIn=" + expiresIn + ", user="
                + user + "]";
    }
}
