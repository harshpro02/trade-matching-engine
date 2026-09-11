package com.harshshah.matchingengine.controller;

import com.harshshah.matchingengine.dto.ApiError;
import com.harshshah.matchingengine.service.OrderNotFoundException;
import com.harshshah.matchingengine.service.UnknownSymbolException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns the exceptions the domain and service layers throw into HTTP status codes.
 *
 * <p>This exists so the layers below it never have to know they are behind HTTP.
 * {@code Order.cancel()} throws {@link IllegalStateException} because cancelling a filled
 * order is meaningless in any context, not because it is a 409; mapping that here keeps the
 * domain usable from a test, a batch job, or anything else that never speaks HTTP.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** A symbol with no instruments row cannot be traded, and never could be. */
    @ExceptionHandler(UnknownSymbolException.class)
    public ResponseEntity<ApiError> unknownSymbol(UnknownSymbolException exception) {
        return status(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ApiError> orderNotFound(OrderNotFoundException exception) {
        return status(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    /**
     * 409 rather than 400: cancelling a filled order is a well-formed request that conflicts
     * with the order's current state. The same request was valid a moment earlier.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> conflict(IllegalStateException exception) {
        return status(HttpStatus.CONFLICT, exception.getMessage());
    }

    /** Domain guards - overfilling, non-positive quantities, a mismatched book. */
    @ExceptionHandler({IllegalArgumentException.class, NullPointerException.class})
    public ResponseEntity<ApiError> badRequest(RuntimeException exception) {
        return status(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    /** Bean validation on the request body, reported field by field. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalidRequest(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        exception.getBindingResult().getGlobalErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));

        return ResponseEntity.badRequest().body(ApiError.validation(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "the request failed validation",
                fieldErrors));
    }

    private static ResponseEntity<ApiError> status(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), status.getReasonPhrase(), message));
    }
}
