package com.eofp.order.api;

import com.eofp.order.application.OrderNotFoundException;
import com.eofp.order.application.OrderRequestRejectedException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Turns exceptions into the uniform error body from docs/06 section 1.2.
 * Internal details never leave the server: unexpected errors return a generic message
 * and the real cause goes to the log, tagged with the same traceId.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                             HttpServletRequest request) {
        List<ApiErrorResponse.FieldIssue> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiErrorResponse.FieldIssue(error.getField(), error.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", details, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Request body is missing or is not valid JSON", List.of(), request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingHeader(MissingRequestHeaderException ex,
                                                                HttpServletRequest request) {
        return unauthorized(request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                               HttpServletRequest request) {
        // A malformed identity header is an authentication problem; a malformed path value is a bad request
        if (ex.getParameter().hasParameterAnnotation(RequestHeader.class)) {
            return unauthorized(request);
        }
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Invalid value for '" + ex.getName() + "'", List.of(), request);
    }

    @ExceptionHandler(OrderRequestRejectedException.class)
    public ResponseEntity<ApiErrorResponse> handleRejected(OrderRequestRejectedException ex,
                                                           HttpServletRequest request) {
        return switch (ex.getReason()) {
            case DUPLICATE_PRODUCT ->
                    build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", ex.getMessage(), List.of(), request);
            case PRODUCT_NOT_AVAILABLE ->
                    build(HttpStatus.UNPROCESSABLE_ENTITY, "PRODUCT_NOT_AVAILABLE", ex.getMessage(), List.of(), request);
            case MIXED_CURRENCY ->
                    build(HttpStatus.UNPROCESSABLE_ENTITY, "MIXED_CURRENCY", ex.getMessage(), List.of(), request);
        };
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(OrderNotFoundException ex,
                                                           HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "Order not found", List.of(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        ResponseEntity<ApiErrorResponse> response = build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred", List.of(), request);
        log.error("Unexpected error, traceId={}", response.getBody().traceId(), ex);
        return response;
    }

    private static ResponseEntity<ApiErrorResponse> unauthorized(HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required", List.of(), request);
    }

    private static ResponseEntity<ApiErrorResponse> build(HttpStatus status, String code, String message,
                                                          List<ApiErrorResponse.FieldIssue> details,
                                                          HttpServletRequest request) {
        String traceId = request.getHeader("X-Correlation-Id");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        ApiErrorResponse body = new ApiErrorResponse(
                new ApiErrorResponse.ErrorBody(code, message, details),
                Instant.now(),
                request.getRequestURI(),
                traceId);
        return ResponseEntity.status(status).body(body);
    }
}
