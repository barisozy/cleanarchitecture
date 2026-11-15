package com.barisozy.cleanarchitecture.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.barisozy.cleanarchitecture.web.config.properties.ApiProperties;
import com.barisozy.cleanarchitecture.web.error.ProblemDetailFactory;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import tools.jackson.databind.json.JsonMapper;

/** Covers the RFC 9457 envelope written by the security filter chain. */
class SecurityErrorHandlerTest {

    private final ProblemDetailFactory problems =
            new ProblemDetailFactory(new ApiProperties("https://example.test/problems", "http://localhost:8080"));
    private final SecurityErrorHandler handler =
            new SecurityErrorHandler(problems, JsonMapper.builder().build());

    @Test
    void rendersMissingCredentialsAsUnauthorizedAndSetsTheBearerChallenge() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/todos");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(request, response, new InsufficientAuthenticationException("No token"));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertThat(body)
                .contains("\"type\":\"https://example.test/problems/security.unauthorized\"")
                .contains("\"title\":\"Unauthorized\"")
                .contains("\"status\":401")
                .contains("\"instance\":\"/api/todos\"")
                .contains("\"code\":\"security.unauthorized\"");
    }

    @Test
    void rendersAnAuthorizationFailureAsForbidden() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(
                new MockHttpServletRequest("GET", "/api/todos"), response, new AccessDeniedException("Not allowed"));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .contains("\"status\":403")
                .contains("\"code\":\"security.forbidden\"");
    }

    @Test
    void leavesACommittedResponseUntouched() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCommitted(true);

        handler.commence(
                new MockHttpServletRequest("GET", "/api/todos"),
                response,
                new InsufficientAuthenticationException("No token"));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getContentAsByteArray()).isEmpty();
    }
}
