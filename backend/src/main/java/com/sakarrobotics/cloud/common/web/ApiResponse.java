package com.sakarrobotics.cloud.common.web;

import com.sakarrobotics.cloud.common.error.ApiErrorBody;

/** Standard success envelope: {@code {"data": ..., "error": null}}. */
public record ApiResponse<T>(T data, ApiErrorBody error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data, null);
    }
}
