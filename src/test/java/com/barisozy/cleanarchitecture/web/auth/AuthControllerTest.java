package com.barisozy.cleanarchitecture.web.auth;

import static com.barisozy.cleanarchitecture.web.WebSliceTestConfiguration.jwtFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.barisozy.cleanarchitecture.application.ApplicationException;
import com.barisozy.cleanarchitecture.application.auth.AuthResult;
import com.barisozy.cleanarchitecture.application.auth.AuthService;
import com.barisozy.cleanarchitecture.application.auth.UserView;
import com.barisozy.cleanarchitecture.domain.user.RoleName;
import com.barisozy.cleanarchitecture.domain.user.User;
import com.barisozy.cleanarchitecture.fixtures.UserTestData;
import com.barisozy.cleanarchitecture.web.WebSliceTestConfiguration;
import com.barisozy.cleanarchitecture.web.error.GlobalExceptionHandler;
import com.barisozy.cleanarchitecture.web.error.ProblemDetailFactory;
import com.barisozy.cleanarchitecture.web.security.SecurityConfiguration;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Covers the status codes and the response envelope of the authentication endpoints. */
@WebMvcTest(AuthController.class)
@Import({
    SecurityConfiguration.class,
    WebSliceTestConfiguration.class,
    ProblemDetailFactory.class,
    GlobalExceptionHandler.class
})
@ActiveProfiles("test")
class AuthControllerTest {

    private static final String CREDENTIALS = """
            {"email":"user@example.com","password":"StrongPassword123!"}
            """;

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private AuthService authService;

    /** A user with a fixed identity, so that the assertions stay deterministic. */
    private final User user = UserTestData.withFixedId();

    private final AuthResult authResult = new AuthResult("access-token", "Bearer", 3600L, UserView.from(user));

    @Test
    void registersTheUserAndRejectsAnInvalidBody() {
        when(authService.register(any(), any())).thenReturn(authResult);

        MvcTestResult registered = mvc.post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREDENTIALS)
                .exchange();

        assertThat(registered).hasStatus(HttpStatus.CREATED);
        assertThat(registered).hasHeader(HttpHeaders.LOCATION, "/api/auth/me");
        assertThat(registered)
                .bodyJson()
                .extractingPath("$.accessToken")
                .asString()
                .isEqualTo("access-token");
        assertThat(registered)
                .bodyJson()
                .extractingPath("$.tokenType")
                .asString()
                .isEqualTo("Bearer");
        assertThat(registered)
                .bodyJson()
                .extractingPath("$.expiresIn")
                .convertTo(InstanceOfAssertFactories.LONG)
                .isGreaterThan(0L);
        assertThat(registered)
                .bodyJson()
                .extractingPath("$.user.id")
                .asString()
                .isEqualTo(user.id().value().toString());
        assertThat(registered)
                .bodyJson()
                .extractingPath("$.user.email")
                .asString()
                .isEqualTo(UserTestData.USER_EMAIL);
        assertThat(registered)
                .bodyJson()
                .extractingPath("$.user.role")
                .asString()
                .isEqualTo("USER");
        assertThat(registered.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();

        clearInvocations(authService);
        MvcTestResult invalid = mvc.post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"not-an-email","password":"short"}
                        """)
                .exchange();

        assertThat(invalid)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(invalid).bodyJson().extractingPath("$.code").asString().isEqualTo("validation.failed");
        assertThat(invalid)
                .bodyJson()
                .extractingPath("$.fieldErrors")
                .asArray()
                .extracting("field")
                .contains("email", "password");
        verifyNoInteractions(authService);
    }

    @Test
    void logsInAndMapsEveryInvalidCredentialToUnauthorized() {
        when(authService.login(any(), any())).thenReturn(authResult);

        MvcTestResult loggedIn = mvc.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREDENTIALS)
                .exchange();

        assertThat(loggedIn).hasStatus(HttpStatus.OK);
        assertThat(loggedIn)
                .bodyJson()
                .extractingPath("$.accessToken")
                .asString()
                .isEqualTo("access-token");
        assertThat(loggedIn).bodyJson().extractingPath("$.tokenType").asString().isEqualTo("Bearer");
        assertThat(loggedIn.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();

        when(authService.login(any(), any()))
                .thenThrow(ApplicationException.unauthorized("auth.invalid-credentials", "Invalid email or password."));
        MvcTestResult wrongCredentials = login("user@example.com", "WrongPassword123!");
        MvcTestResult malformedEmail = login("not-an-email", "StrongPassword123!");
        MvcTestResult oversizedPassword = login("user@example.com", "a".repeat(73));

        assertThat(wrongCredentials)
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(wrongCredentials)
                .bodyJson()
                .extractingPath("$.code")
                .asString()
                .isEqualTo("auth.invalid-credentials");
        assertThat(malformedEmail).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(malformedEmail)
                .bodyJson()
                .extractingPath("$.code")
                .asString()
                .isEqualTo("auth.invalid-credentials");
        assertThat(oversizedPassword).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(oversizedPassword)
                .bodyJson()
                .extractingPath("$.code")
                .asString()
                .isEqualTo("auth.invalid-credentials");
    }

    @Test
    void returnsTheAuthenticatedProfileAndRejectsAMissingToken() {
        when(authService.currentUser(any())).thenReturn(UserView.from(user));

        MvcTestResult profile = mvc.get().uri("/api/auth/me").with(jwtFor(user)).exchange();

        assertThat(profile).hasStatus(HttpStatus.OK);
        assertThat(profile)
                .bodyJson()
                .extractingPath("$.id")
                .asString()
                .isEqualTo(user.id().value().toString());
        assertThat(profile).bodyJson().extractingPath("$.email").asString().isEqualTo(UserTestData.USER_EMAIL);
        assertThat(profile).bodyJson().extractingPath("$.role").asString().isEqualTo(RoleName.USER.name());
        assertThat(profile.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();

        MvcTestResult anonymous = mvc.get().uri("/api/auth/me").exchange();

        assertThat(anonymous)
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(anonymous).bodyJson().extractingPath("$.code").asString().isEqualTo("security.unauthorized");
        assertThat(anonymous).hasHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        assertThat(anonymous.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    private MvcTestResult login(String email, String password) {
        return mvc.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password))
                .exchange();
    }
}
