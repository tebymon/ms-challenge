package com.tenpo.challenge.ratelimit;

import com.tenpo.challenge.config.ClientIpResolver;
import com.tenpo.challenge.entity.HistoryEntity;
import com.tenpo.challenge.service.HistoryService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;

@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final Bucket bucket;
    private final HistoryService historyService;

    public RateLimitInterceptor(
            @Value("${rate-limit.capacity:3}") long capacity,
            @Value("${rate-limit.refill-tokens:3}") long refillTokens,
            @Value("${rate-limit.refill-period-minutes:1}") long refillPeriodMinutes,
            HistoryService historyService
    ) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(refillTokens, Duration.ofMinutes(refillPeriodMinutes))
                .build();

        this.bucket = Bucket.builder()
                .addLimit(limit)
                .build();
        this.historyService = historyService;
    }

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler
    ) {
        String uri = request.getRequestURI();
        if (isExcluded(uri)) {
            return true;
        }

        var probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            response.setHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            return true;
        }

        long retryAfterSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000L;
        log.warn("Rate limit exceeded for {} {} from {}. Retry-After: {}s",
                request.getMethod(), uri, ClientIpResolver.resolve(request), retryAfterSeconds);

        saveRateLimitHistory(request, retryAfterSeconds);

        try {
            writeRateLimitResponse(response, retryAfterSeconds);
        } catch (IOException e) {
            log.error("Failed to write rate limit response", e);
        }
        return false;
    }

    private void saveRateLimitHistory(HttpServletRequest request, long retryAfterSeconds) {
        if (historyService == null) {
            return;
        }
        try {
            historyService.saveAsync(HistoryEntity.builder()
                    .calledAt(OffsetDateTime.now())
                    .endpoint(request.getRequestURI())
                    .httpMethod(request.getMethod())
                    .parameters(null)
                    .response(null)
                    .error("Rate limit exceeded. Retry after " + retryAfterSeconds + " seconds.")
                    .statusCode(HttpStatus.TOO_MANY_REQUESTS.value())
                    .durationMs(0L)
                    .clientIp(ClientIpResolver.resolve(request))
                    .build());
        } catch (Exception e) {
            log.warn("Failed to save rate limit event to history: {}", e.getMessage());
        }
    }

    private void writeRateLimitResponse(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"status\":429,\"error\":\"Too Many Requests\"," +
                "\"message\":\"Rate limit exceeded. Maximum 3 requests per minute. Retry after "
                + retryAfterSeconds + " seconds.\"}"
        );
    }

    private boolean isExcluded(String uri) {
        return uri.startsWith("/swagger-ui")
                || uri.startsWith("/api-docs")
                || uri.startsWith("/actuator");
    }
}
