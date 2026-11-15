package com.barisozy.cleanarchitecture.web.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.barisozy.cleanarchitecture.application.auth.AuthResult;
import com.barisozy.cleanarchitecture.application.auth.UserView;
import com.barisozy.cleanarchitecture.fixtures.UserTestData;
import com.barisozy.cleanarchitecture.web.auth.dto.AuthResponse;
import com.barisozy.cleanarchitecture.web.auth.dto.LoginRequest;
import com.barisozy.cleanarchitecture.web.auth.dto.RegisterRequest;
import org.junit.jupiter.api.Test;

/** The credential-bearing records never render their secrets. */
class AuthDtoTest {

    @Test
    void redactsPasswordsAndAccessTokensInToString() {
        String password = "StrongPassword123!";
        String token = "a-usable-access-token";

        assertThat(new RegisterRequest("user@example.com", password).toString())
                .doesNotContain(password)
                .contains("********");
        assertThat(new LoginRequest("user@example.com", password).toString())
                .doesNotContain(password)
                .contains("********");

        AuthResponse response =
                AuthResponse.from(new AuthResult(token, "Bearer", 3600L, UserView.from(UserTestData.withFixedId())));
        assertThat(response.toString()).doesNotContain(token).contains("********");
    }
}
