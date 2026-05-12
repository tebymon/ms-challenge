package com.tenpo.challenge.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "Parameters for the calculation with dynamic percentage")
public record CalculationRequest(

        @NotNull(message = "num1 is required")
        @Schema(description = "First number", example = "5")
        BigDecimal num1,

        @NotNull(message = "num2 is required")
        @Schema(description = "Second number", example = "5")
        BigDecimal num2
) {}
