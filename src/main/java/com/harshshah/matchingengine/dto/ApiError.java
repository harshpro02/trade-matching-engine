package com.harshshah.matchingengine.dto;

import java.time.Instant;
import java.util.Map;

/**
 * A failure, in a shape a caller can act on.
 *
 * <p>{@code fieldErrors} is populated only for request validation failures, where naming
 * the offending field is the difference between a caller fixing their request and guessing.
 */
public record ApiError(Instant timestamp,
                       int status,
                       String error,
                       String message,
                       Map<String, String> fieldErrors) {

    public static ApiError of(int status, String error, String message) {
        return new ApiError(Instant.now(), status, error, message, Map.of());
    }

    public static ApiError validation(int status, String error, String message,
                                      Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), status, error, message, fieldErrors);
    }
}
