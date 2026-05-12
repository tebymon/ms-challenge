package com.tenpo.challenge.ratelimit;

import com.tenpo.challenge.service.HistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("RateLimitInterceptor")
class RateLimitInterceptorTest {

    private HistoryService historyService;

    @BeforeEach
    void setUp() {
        historyService = mock(HistoryService.class);
    }

    private MockHttpServletRequest calculateRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/calculate");
        request.setRequestURI("/api/v1/calculate");
        request.setRemoteAddr("10.0.0.1");
        return request;
    }

    @Test
    @DisplayName("Allows requests within the capacity limit")
    void allowsRequestsWithinLimit() {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(3, 3, 1, historyService);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(calculateRequest(), response, new Object())).isTrue();
        assertThat(interceptor.preHandle(calculateRequest(), response, new Object())).isTrue();
        assertThat(interceptor.preHandle(calculateRequest(), response, new Object())).isTrue();
        verifyNoInteractions(historyService);
    }

    @Test
    @DisplayName("Blocks the request that exceeds the capacity, returns 429 and persists in history")
    void blocksRequestExceedingLimit() {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(3, 3, 1, historyService);
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(calculateRequest(), new MockHttpServletResponse(), new Object());
        interceptor.preHandle(calculateRequest(), new MockHttpServletResponse(), new Object());
        interceptor.preHandle(calculateRequest(), new MockHttpServletResponse(), new Object());

        boolean allowed = interceptor.preHandle(calculateRequest(), response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(response.getHeader("Retry-After")).isNotNull();
        verify(historyService, times(1)).saveAsync(any());
    }

    @Test
    @DisplayName("Excludes Swagger / api-docs / actuator paths from rate limiting")
    void excludesNonRateLimitedPaths() {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(1, 1, 1, historyService);
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockHttpServletRequest swagger = new MockHttpServletRequest("GET", "/swagger-ui/index.html");
        swagger.setRequestURI("/swagger-ui/index.html");

        for (int i = 0; i < 5; i++) {
            assertThat(interceptor.preHandle(swagger, response, new Object())).isTrue();
        }
        verifyNoInteractions(historyService);
    }

    @Test
    @DisplayName("Applies rate limit to /api/v1/history (no longer excluded)")
    void appliesRateLimitToHistoryEndpoint() {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(1, 1, 1, historyService);

        MockHttpServletRequest historyRequest = new MockHttpServletRequest("GET", "/api/v1/history");
        historyRequest.setRequestURI("/api/v1/history");

        assertThat(interceptor.preHandle(historyRequest, new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(interceptor.preHandle(historyRequest, new MockHttpServletResponse(), new Object())).isFalse();
    }
}
