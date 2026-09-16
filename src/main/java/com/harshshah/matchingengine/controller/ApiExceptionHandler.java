package com.harshshah.matchingengine.controller;

import com.harshshah.matchingengine.dto.ApiError;
import com.harshshah.matchingengine.service.OrderNotFoundException;
import com.harshshah.matchingengine.service.SymbolAlreadyExistsException;
import com.harshshah.matchingengine.service.UnknownSymbolException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({UnknownSymbolException.class, OrderNotFoundException.class})
    public ResponseEntity<ApiError> notFound(RuntimeException exception) {
        return status(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({IllegalStateException.class, SymbolAlreadyExistsException.class})
    public ResponseEntity<ApiError> conflict(RuntimeException exception) {
        return status(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> badRequest(IllegalArgumentException exception) {
        return status(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> invalidParameter(HandlerMethodValidationException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getAllValidationResults().forEach(result ->
                result.getResolvableErrors().forEach(error ->
                        fieldErrors.putIfAbsent(
                                result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())));

        return ResponseEntity.badRequest().body(ApiError.validation(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "the request failed validation",
                fieldErrors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> constraintViolation(ConstraintViolationException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation ->
                fieldErrors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));

        return ResponseEntity.badRequest().body(ApiError.validation(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "the request failed validation",
                fieldErrors));
    }

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
