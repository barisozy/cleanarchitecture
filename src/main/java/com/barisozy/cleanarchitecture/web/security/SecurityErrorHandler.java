package com.barisozy.cleanarchitecture.web.security;

import com.barisozy.cleanarchitecture.web.error.ProblemDetailFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Renders authentication and authorization failures as RFC 9457 responses.
 *
 * <p>The security filter chain runs outside Spring MVC, so it cannot use the exception handler.
 * This class is the equivalent boundary for the filters and writes the same envelope.
 */
@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ProblemDetailFactory problems;
    private final ObjectMapper objectMapper;

    public SecurityErrorHandler(ProblemDetailFactory problems, ObjectMapper objectMapper) {
        this.problems = problems;
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(request, response, HttpStatus.UNAUTHORIZED, "security.unauthorized", "Authentication is required.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        write(
                request,
                response,
                HttpStatus.FORBIDDEN,
                "security.forbidden",
                "You do not have permission to perform this action.");
    }

    private void write(
            HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code, String detail)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        ProblemDetail problem = problems.create(status, code, detail, request.getRequestURI());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
