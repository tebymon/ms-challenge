package com.tenpo.challenge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenpo.challenge.exception.ExternalServiceException;
import com.tenpo.challenge.model.request.CalculationRequest;
import com.tenpo.challenge.model.response.CalculationResponse;
import com.tenpo.challenge.proxy.service.PercentageService;
import com.tenpo.challenge.service.impl.CalculatorServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CalculatorService")
class CalculatorServiceTest {

    @Mock
    private PercentageService percentageService;

    @Mock
    private HistoryService historyService;

    private CalculatorService calculatorService;

    @BeforeEach
    void setUp() {
        calculatorService = new CalculatorServiceImpl(percentageService, historyService, new ObjectMapper());
    }

    @Test
    @DisplayName("Successful calculation: (5 + 5) + 10% = 11.00")
    void shouldCalculateCorrectly() {
        when(percentageService.fetchPercentage()).thenReturn(new BigDecimal("10"));
        doNothing().when(historyService).saveAsync(any());

        CalculationResponse response = calculatorService.calculate(
                new CalculationRequest(new BigDecimal("5"), new BigDecimal("5"))
        );

        assertThat(response.result()).isEqualByComparingTo(new BigDecimal("11.00"));
        assertThat(response.percentageApplied()).isEqualByComparingTo(new BigDecimal("10"));
        verify(percentageService, times(1)).fetchPercentage();
        verify(historyService, times(1)).saveAsync(any());
    }

    @Test
    @DisplayName("External service failure propagates ExternalServiceException — history saved by GlobalExceptionHandler")
    void shouldPropagateExceptionWhenExternalFails() {
        when(percentageService.fetchPercentage())
                .thenThrow(new ExternalServiceException("Service unavailable"));

        assertThatThrownBy(() -> calculatorService.calculate(
                new CalculationRequest(new BigDecimal("5"), new BigDecimal("5"))
        ))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("unavailable");

        verify(historyService, never()).saveAsync(any());
    }

    @Test
    @DisplayName("Percentage from external service is applied correctly")
    void shouldApplyDifferentPercentages() {
        when(percentageService.fetchPercentage()).thenReturn(new BigDecimal("20"));
        doNothing().when(historyService).saveAsync(any());

        CalculationResponse response = calculatorService.calculate(
                new CalculationRequest(new BigDecimal("100"), new BigDecimal("0"))
        );

        assertThat(response.result()).isEqualByComparingTo(new BigDecimal("120.00"));
    }
}
