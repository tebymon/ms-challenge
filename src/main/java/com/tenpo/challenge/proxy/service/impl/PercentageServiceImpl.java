package com.tenpo.challenge.proxy.service.impl;

import com.tenpo.challenge.exception.ExternalServiceException;
import com.tenpo.challenge.proxy.api.PercentageApi;
import com.tenpo.challenge.proxy.api.PercentageResponse;
import com.tenpo.challenge.proxy.service.PercentageService;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import retrofit2.Response;

import java.io.IOException;
import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class PercentageServiceImpl implements PercentageService {

    private final PercentageApi percentageApi;

    @Override
    @Retry(name = "externalPercentage", fallbackMethod = "fallback")
    public BigDecimal fetchPercentage() {
        log.info("Fetching percentage from external service...");
        try {
            Response<PercentageResponse> response = percentageApi.getPercentage().execute();

            if (!response.isSuccessful() || response.body() == null) {
                throw new IllegalStateException(
                        "Invalid response from external service: HTTP " + response.code()
                );
            }

            log.info("Percentage received: {}", response.body().percentage());
            return response.body().percentage();

        } catch (IOException e) {
            throw new ExternalServiceException(
                    "Network error reaching external percentage service", e
            );
        }
    }

    public BigDecimal fallback(Exception ex) {
        log.error("External service unavailable after 3 attempts. Error: {}", ex.getMessage());
        throw new ExternalServiceException(
                "External percentage service is unavailable. Please try again later.", ex
        );
    }
}
