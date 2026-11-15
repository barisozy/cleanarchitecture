package com.barisozy.cleanarchitecture.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.barisozy.cleanarchitecture.domain.user.RoleName;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Covers the authentication flow over HTTP: registration, login and the profile of the
 * authenticated user.
 *
 * <p>The assertions observe the contract of the API, not the internals: the status codes, the error
 * codes of the problem details and the absence of a cookie flow.
 */
class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    void registersLogsInAndReadsTheCurrentUser() {
        String email = uniqueEmail("end-to-end");

        MvcTestResult registration = mvc.post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password()))
                .exchange();

        assertThat(registration).hasStatus(HttpStatus.CREATED);
        assertThat(registration).hasHeader(HttpHeaders.LOCATION, "/api/auth/me");
        assertThat(registration)
                .bodyJson()
                .extractingPath("$.accessToken")
                .asString()
                .isNotBlank();
        assertThat(registration)
                .bodyJson()
                .extractingPath("$.tokenType")
                .asString()
                .isEqualTo("Bearer");
        assertThat(registration)
                .bodyJson()
                .extractingPath("$.expiresIn")
                .convertTo(InstanceOfAssertFactories.LONG)
                .isGreaterThan(0L);
        assertThat(registration)
                .bodyJson()
                .extractingPath("$.user.email")
                .asString()
                .isEqualTo(email);
        assertThat(registration)
                .bodyJson()
                .extractingPath("$.user.role")
                .asString()
                .isEqualTo(RoleName.USER.name());

        String token = login(email, password());

        assertThat(mvc.get().uri("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.email")
                .asString()
                .isEqualTo(email);

        // The access token travels in the Authorization header. No endpoint writes a cookie, so the
        // API has no CSRF surface and no session.
        assertThat(registration.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .isEmpty();
    }

    @Test
    void rejectsInvalidCredentialsWithTheSameCode() {
        String email = uniqueEmail("wrong-password");
        register(email);

        // A wrong password, an unknown address and a malformed address are indistinguishable: the
        // same 401 and the same code, so that the endpoint cannot be used to probe for accounts.
        assertInvalidCredentials(email, "WrongPassword123!");
        assertInvalidCredentials(uniqueEmail("unknown"), password());
        assertInvalidCredentials("not-an-email", password());
    }

    @Test
    void rejectsASecondRegistrationWithTheSameEmail() {
        String email = uniqueEmail("duplicate");
        register(email);

        MvcTestResult result = mvc.post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password()))
                .exchange();

        assertThat(result)
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.code")
                .asString()
                .isEqualTo("auth.email-already-registered");
    }

    /**
     * The byte limit of the password algorithm is a domain rule, not a format rule of the request.
     * A login with such a password is simply wrong (401), a registration breaks an invariant (422).
     */
    @Test
    void rejectsAPasswordThatExceedsTheLimitOfTheAlgorithm() {
        String tooLong = "a".repeat(73);

        MvcTestResult login = mvc.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(uniqueEmail("long-password"), tooLong))
                .exchange();
        assertThat(login)
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.code")
                .asString()
                .isEqualTo("auth.invalid-credentials");

        MvcTestResult registration = mvc.post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(uniqueEmail("long-registration"), tooLong))
                .exchange();
        assertThat(registration)
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson()
                .extractingPath("$.code")
                .asString()
                .isEqualTo("user.password.too-long");
    }

    @Test
    void rejectsAnInvalidRequest() {
        MvcTestResult malformed = mvc.post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"not-an-email","password":"short"}
                        """)
                .exchange();
        assertThat(malformed)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.code")
                .asString()
                .isEqualTo("validation.failed");
        assertThat(malformed)
                .bodyJson()
                .extractingPath("$.fieldErrors")
                .asArray()
                .extracting("field")
                .contains("email", "password");

        MvcTestResult anonymous = mvc.get().uri("/api/auth/me").exchange();
        assertThat(anonymous)
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .hasHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .bodyJson()
                .extractingPath("$.code")
                .asString()
                .isEqualTo("security.unauthorized");
    }

    private void assertInvalidCredentials(String email, String rawPassword) {
        MvcTestResult result = mvc.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, rawPassword))
                .exchange();

        assertThat(result)
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.code")
                .asString()
                .isEqualTo("auth.invalid-credentials");
    }
}
