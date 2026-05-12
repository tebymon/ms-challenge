package com.tenpo.challenge.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenpo.challenge.config.ClientIpResolver;
import com.tenpo.challenge.proxy.service.PercentageService;
import com.tenpo.challenge.entity.HistoryEntity;
import com.tenpo.challenge.model.dto.CalculationDto;
import com.tenpo.challenge.model.request.CalculationRequest;
import com.tenpo.challenge.model.response.CalculationResponse;
import com.tenpo.challenge.model.response.HistoryResponse;
import com.tenpo.challenge.service.CalculatorService;
import com.tenpo.challenge.service.HistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalculatorServiceImpl implements CalculatorService {

    private final PercentageService percentageService;
    private final HistoryService historyService;
    private final ObjectMapper objectMapper;

    @Override
    public CalculationResponse calculate(CalculationRequest request) {
        long start = System.currentTimeMillis();

        CalculationDto dto = CalculationDto.compute(
                request.num1(),
                request.num2(),
                percentageService.fetchPercentage()
        );

        CalculationResponse response = new CalculationResponse(
                dto.num1(), dto.num2(), dto.percentageApplied(), dto.result()
        );

        historyService.saveAsync(HistoryEntity.builder()
                .calledAt(OffsetDateTime.now())
                .endpoint("/api/v1/calculate")
                .httpMethod("POST")
                .parameters(writeJson(request))
                .response(writeJson(response))
                .error(null)
                .statusCode(200)
                .durationMs(System.currentTimeMillis() - start)
                .clientIp(ClientIpResolver.resolveFromContext())
                .build());

        return response;
    }

    @Override
    public Page<HistoryResponse> getHistory(int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("calledAt").descending());
        return historyService.findAll(pageable);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize value to JSON for history: {}", e.getMessage());
            return null;
        }
    }
}
