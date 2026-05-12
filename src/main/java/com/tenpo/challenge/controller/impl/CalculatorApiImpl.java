package com.tenpo.challenge.controller.impl;

import com.tenpo.challenge.controller.api.CalculatorApi;
import com.tenpo.challenge.model.request.CalculationRequest;
import com.tenpo.challenge.model.response.CalculationResponse;
import com.tenpo.challenge.model.response.HistoryResponse;
import com.tenpo.challenge.service.CalculatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class CalculatorApiImpl implements CalculatorApi {

    private final CalculatorService calculatorService;

    @Override
    @PostMapping("/calculate")
    public ResponseEntity<CalculationResponse> calculate(@RequestBody CalculationRequest request) {
        return ResponseEntity.ok(calculatorService.calculate(request));
    }

    @Override
    @GetMapping("/history")
    public ResponseEntity<Page<HistoryResponse>> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(calculatorService.getHistory(page, size));
    }
}
