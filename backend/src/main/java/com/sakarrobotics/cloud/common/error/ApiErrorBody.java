package com.sakarrobotics.cloud.common.error;

import java.time.Instant;
import java.util.List;

/**
 * Standard error envelope: {@code {"data": null, "error": {...}}} per
 * SAKAR_ROBOT_PLATFORM_API_SPEC.md §1 conventions.
 */
public record ApiErrorBody(
        String code,
        String message,
        String requestId,
        Instant timestamp,
        List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {
    }

    public static ApiErrorBody of(SakarErrorCode code, String message, String requestId) {
        return new ApiErrorBody(code.name(), message, requestId, Instant.now(), null);
    }

    public static ApiErrorBody validation(String requestId, List<FieldError> fieldErrors) {
        return new ApiErrorBody(
                SakarErrorCode.VALIDATION_FAILED.name(),
                "Request validation failed",
                requestId,
                Instant.now(),
                fieldErrors);
    }
}
