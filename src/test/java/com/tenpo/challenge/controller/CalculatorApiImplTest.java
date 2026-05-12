package com.tenpo.challenge.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenpo.challenge.controller.impl.CalculatorApiImpl;
import com.tenpo.challenge.exception.ExternalServiceException;
import com.tenpo.challenge.exception.GlobalExceptionHandler;
import com.tenpo.challenge.model.request.CalculationRequest;
import com.tenpo.challenge.model.response.CalculationResponse;
import com.tenpo.challenge.ratelimit.RateLimitInterceptor;
import com.tenpo.challenge.service.CalculatorService;
import com.tenpo.challenge.service.HistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CalculatorApiImpl.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class CalculatorApiImplTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CalculatorService calculatorService;

    @MockBean
    private HistoryService historyService;

    @MockBean
    private RateLimitInterceptor rateLimitInterceptor;

    @BeforeEach
    void setUp() throws Exception {
        when(rateLimitInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("POST /api/v1/calculate — happy path returns 200")
    void calculateReturns200() throws Exception {
        when(calculatorService.calculate(any())).thenReturn(
                new CalculationResponse(new BigDecimal("5"), new BigDecimal("5"),
                        new BigDecimal("10"), new BigDecimal("11.00"))
        );

        mockMvc.perform(post("/api/v1/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CalculationRequest(new BigDecimal("5"), new BigDecimal("5"))
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(11.00))
                .andExpect(jsonPath("$.percentageApplied").value(10));
    }

    @Test
    @DisplayName("POST /api/v1/calculate — null num1 returns 400 and saves history")
    void calculateValidationErrorSavesHistory() throws Exception {
        mockMvc.perform(post("/api/v1/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"num2\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("num1")));

        verify(historyService, times(1)).saveAsync(any());
    }

    @Test
    @DisplayName("POST /api/v1/calculate — malformed JSON returns 400 and saves history")
    void calculateMalformedJsonSavesHistory() throws Exception {
        mockMvc.perform(post("/api/v1/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"num1\":\"1a\",\"num2\":5}"))
                .andExpect(status().isBadRequest());

        verify(historyService, times(1)).saveAsync(any());
    }

    @Test
    @DisplayName("POST /api/v1/calculate — external service unavailable returns 503 and saves history")
    void calculateExternalServiceErrorSavesHistory() throws Exception {
        when(calculatorService.calculate(any()))
                .thenThrow(new ExternalServiceException("External service unavailable"));

        mockMvc.perform(post("/api/v1/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CalculationRequest(new BigDecimal("5"), new BigDecimal("5"))
                        )))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));

        verify(historyService, times(1)).saveAsync(any());
    }
}
