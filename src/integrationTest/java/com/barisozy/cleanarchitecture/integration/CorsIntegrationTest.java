package com.barisozy.cleanarchitecture.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Covers the browser-facing CORS policy over the real security chain. */
class CorsIntegrationTest extends AbstractIntegrationTest {

    @Test
    void allowsAConfiguredOriginAndRejectsAnUnknownOne() {
        MvcTestResult allowed = mvc.options()
                .uri("/api/todos")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange();

        assertThat(allowed).hasStatusOk().hasHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000");

        MvcTestResult rejected = mvc.options()
                .uri("/api/todos")
                .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange();

        assertThat(rejected).hasStatus(HttpStatus.FORBIDDEN);
    }
}
