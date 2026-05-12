package com.tenpo.challenge.controller.api;

import com.tenpo.challenge.model.request.CalculationRequest;
import com.tenpo.challenge.model.response.CalculationResponse;
import com.tenpo.challenge.model.response.HistoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;

@Tag(name = "Calculator API", description = "Endpoints for the Tenpo technical challenge")
public interface CalculatorApi {

    @Operation(
            summary = "Calculate with dynamic percentage",
            description = "Adds num1 + num2 and applies a percentage obtained from an external service."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Calculation successful"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded"),
            @ApiResponse(responseCode = "503", description = "External service unavailable")
    })
    ResponseEntity<CalculationResponse> calculate(@Valid CalculationRequest request);

    @Operation(
            summary = "Call history",
            description = "Returns paginated history of all API calls, ordered by date descending."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "History retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid pagination parameters")
    })
    ResponseEntity<Page<HistoryResponse>> getHistory(
            @Parameter(description = "Page number (0-based)", example = "0") @Min(0) int page,
            @Parameter(description = "Page size (max 100)", example = "20") @Min(1) @Max(100) int size
    );
}
