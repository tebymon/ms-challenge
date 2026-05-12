package com.tenpo.challenge.model.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CalculationDto - Calculation logic")
class CalculationDtoTest {

    @Test
    @DisplayName("Base case: (5 + 5) + 10% = 11.00")
    void shouldCalculateCorrectly() {
        var result = CalculationDto.compute(
                new BigDecimal("5"),
                new BigDecimal("5"),
                new BigDecimal("10")
        );

        assertThat(result.result()).isEqualByComparingTo(new BigDecimal("11.00"));
        assertThat(result.percentageApplied()).isEqualByComparingTo(new BigDecimal("10"));
    }

    @ParameterizedTest(name = "({0} + {1}) + {2}% = {3}")
    @CsvSource({
            "5,   5,   10,  11.00",
            "100, 0,   10,  110.00",
            "0,   0,   50,  0.00",
            "10,  20,  0,   30.00",
            "1,   1,   100, 4.00"
    })
    @DisplayName("Parameterized calculation cases")
    void shouldHandleVariousInputs(String n1, String n2, String pct, String expected) {
        var result = CalculationDto.compute(
                new BigDecimal(n1),
                new BigDecimal(n2),
                new BigDecimal(pct)
        );
        assertThat(result.result()).isEqualByComparingTo(new BigDecimal(expected));
    }

    @Test
    @DisplayName("BigDecimal preserves precision — no double rounding errors")
    void shouldPreservePrecision() {
        var result = CalculationDto.compute(
                new BigDecimal("0.1"),
                new BigDecimal("0.2"),
                new BigDecimal("10")
        );
        assertThat(result.result()).isEqualByComparingTo(new BigDecimal("0.33"));
    }
}
