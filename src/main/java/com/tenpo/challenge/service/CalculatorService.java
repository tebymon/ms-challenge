package com.tenpo.challenge.service;

import com.tenpo.challenge.model.request.CalculationRequest;
import com.tenpo.challenge.model.response.CalculationResponse;
import com.tenpo.challenge.model.response.HistoryResponse;
import org.springframework.data.domain.Page;

public interface CalculatorService {
    CalculationResponse calculate(CalculationRequest request);
    Page<HistoryResponse> getHistory(int page, int size);
}
