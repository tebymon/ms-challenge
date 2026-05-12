package com.tenpo.challenge.exception;

import com.tenpo.challenge.config.ClientIpResolver;
import com.tenpo.challenge.entity.HistoryEntity;
import com.tenpo.challenge.model.response.ErrorResponse;
import com.tenpo.challenge.service.HistoryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final HistoryService historyService;

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Unreadable request body: {}", ex.getMessage());
        String message = "Invalid or malformed JSON body";
        saveHistory(request, "Invalid JSON body", 400);
        return ResponseEntity.badRequest().body(new ErrorResponse(400, "Bad Request", message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation error: {}", message);
        saveHistory(request, message, 400);
        return ResponseEntity.badRequest().body(new ErrorResponse(400, "Bad Request", message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        String message = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining(", "));
        log.warn("Constraint violation: {}", message);
        saveHistory(request, message, 400);
        return ResponseEntity.badRequest().body(new ErrorResponse(400, "Bad Request", message));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            NoHandlerFoundException ex, HttpServletRequest request) {
        log.warn("No handler for {} {}", request.getMethod(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(404, "Not Found", "Resource not found"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        log.warn("Method {} not supported for {}", request.getMethod(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ErrorResponse(405, "Method Not Allowed", ex.getMessage()));
    }

    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<ErrorResponse> handleExternalService(
            ExternalServiceException ex, HttpServletRequest request) {
        log.error("External service error: {}", ex.getMessage());
        saveHistory(request, ex.getMessage(), 503);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(503, "Service Unavailable", ex.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntime(RuntimeException ex, HttpServletRequest request) {
        log.error("Unexpected runtime error: {}", ex.getMessage(), ex);
        saveHistory(request, ex.getMessage(), 500);
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse(500, "Internal Server Error", "An unexpected error occurred"));
    }

    private void saveHistory(HttpServletRequest request, String error, int statusCode) {
        String params = getCachedBody(request);
        Long start = (Long) request.getAttribute("requestStartTime");
        long durationMs = start != null ? System.currentTimeMillis() - start : 0L;

        historyService.saveAsync(HistoryEntity.builder()
                .calledAt(OffsetDateTime.now())
                .endpoint(request.getRequestURI())
                .httpMethod(request.getMethod())
                .parameters(params)
                .response(null)
                .error(error)
                .statusCode(statusCode)
                .durationMs(durationMs)
                .clientIp(ClientIpResolver.resolve(request))
                .build());
    }

    private String getCachedBody(HttpServletRequest request) {
        if (request instanceof ContentCachingRequestWrapper wrapper) {
            byte[] body = wrapper.getContentAsByteArray();
            if (body.length > 0) {
                return new String(body, StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
