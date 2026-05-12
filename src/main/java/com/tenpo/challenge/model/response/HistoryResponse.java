package com.tenpo.challenge.model.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "API call history record")
public record HistoryResponse(

        @Schema(description = "Record ID", example = "1")
        Long id,

        @Schema(description = "Timestamp of the call")
        OffsetDateTime calledAt,

        @Schema(description = "Invoked endpoint", example = "/api/v1/calculate")
        String endpoint,

        @Schema(description = "HTTP method", example = "POST")
        String httpMethod,

        @Schema(description = "Received parameters in JSON format")
        String parameters,

        @Schema(description = "Successful response in JSON format")
        String response,

        @Schema(description = "Error message if the call failed")
        String error,

        @Schema(description = "HTTP response status code", example = "200")
        Integer statusCode,

        @Schema(description = "Call duration in milliseconds", example = "45")
        Long durationMs,

        @Schema(description = "Client IP address", example = "192.168.1.10")
        String clientIp
) {}
