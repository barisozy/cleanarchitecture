package com.barisozy.cleanarchitecture.web.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Covers validation and propagation of the request correlation id. */
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void keepsAValidIncomingCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/todos");
        request.addHeader(CorrelationIdFilter.HEADER, "correlation-42");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(
                request,
                response,
                (servletRequest, servletResponse) ->
                        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo("correlation-42"));

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("correlation-42");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void replacesInvalidIdsAndAlwaysSetsTheHeader() throws Exception {
        MockHttpServletRequest invalidRequest = new MockHttpServletRequest("GET", "/api/todos");
        invalidRequest.addHeader(CorrelationIdFilter.HEADER, "injected value with spaces");
        MockHttpServletResponse invalidResponse = new MockHttpServletResponse();
        AtomicReference<String> invalidId = new AtomicReference<>();

        filter.doFilter(
                invalidRequest,
                invalidResponse,
                (servletRequest, servletResponse) -> invalidId.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        assertThat(invalidId.get()).isNotEqualTo("injected value with spaces");
        assertThat(UUID.fromString(invalidId.get())).isNotNull();
        assertThat(invalidResponse.getHeader(CorrelationIdFilter.HEADER)).isEqualTo(invalidId.get());

        MockHttpServletRequest requestWithoutId = new MockHttpServletRequest("GET", "/api/todos");
        MockHttpServletResponse responseWithoutId = new MockHttpServletResponse();
        AtomicReference<String> generatedId = new AtomicReference<>();

        filter.doFilter(
                requestWithoutId,
                responseWithoutId,
                (servletRequest, servletResponse) -> generatedId.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        assertThat(generatedId.get()).isNotBlank();
        assertThat(responseWithoutId.getHeader(CorrelationIdFilter.HEADER)).isEqualTo(generatedId.get());
    }
}
