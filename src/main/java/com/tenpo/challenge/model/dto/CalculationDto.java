package com.tenpo.challenge.model.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record CalculationDto(
        BigDecimal num1,
        BigDecimal num2,
        BigDecimal percentageApplied,
        BigDecimal result
) {
    public static CalculationDto compute(BigDecimal num1, BigDecimal num2, BigDecimal percentage) {
        BigDecimal sum = num1.add(num2);
        BigDecimal factor = percentage.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
        BigDecimal increment = sum.multiply(factor);
        BigDecimal finalResult = sum.add(increment).setScale(2, RoundingMode.HALF_UP);
        return new CalculationDto(num1, num2, percentage, finalResult);
    }
}
