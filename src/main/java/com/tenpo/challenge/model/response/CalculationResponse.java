package com.tenpo.challenge.model.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Result of the calculation with applied percentage")
public record CalculationResponse(

        @Schema(description = "First input number", example = "5.00")
        BigDecimal num1,

        @Schema(description = "Second input number", example = "5.00")
        BigDecimal num2,

        @Schema(description = "Applied percentage (e.g. 10 = 10%)", example = "10.00")
        BigDecimal percentageApplied,

        @Schema(description = "Result: (num1 + num2) + percentage%", example = "11.00")
        BigDecimal result
) {}
