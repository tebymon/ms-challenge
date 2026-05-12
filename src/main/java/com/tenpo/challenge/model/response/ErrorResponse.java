package com.tenpo.challenge.model.response;

public record ErrorResponse(int status, String error, String message) {}
