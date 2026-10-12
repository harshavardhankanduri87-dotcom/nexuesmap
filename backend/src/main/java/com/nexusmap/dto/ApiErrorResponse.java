package com.nexusmap.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
    int status,
    String error,
    String message,
    String path,
    Instant timestamp,
    Map<String, String> validationErrors
) {
    public static ApiErrorResponse of(int status, String error, String message, String path) {
        return new ApiErrorResponse(status, error, message, path, Instant.now(), null);
    }

    public static ApiErrorResponse withValidation(int status, String error, String message, String path, Map<String, String> errors) {
        return new ApiErrorResponse(status, error, message, path, Instant.now(), errors);
    }
}
