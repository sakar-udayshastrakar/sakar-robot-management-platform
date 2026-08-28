package com.sakarrobotics.cloud.common.error;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.sakarrobotics.cloud.common.web.ApiResponse;
import com.sakarrobotics.cloud.common.web.RequestIdFilter;

/**
 * Every error response leaving this backend is shaped as
 * {@code {"data": null, "error": {code, message, requestId, timestamp}}}
 * using the platform's own {@link SakarErrorCode} vocabulary — never a raw
 * stack trace, never a raw vendor error (SAKAR_ROBOT_PLATFORM_API_SPEC.md
 * §1.12, SAKAR_SECURITY_REQUIREMENTS.md §13.A).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException ex) {
        String requestId = RequestIdFilter.currentOrNew();
        if (ex.getErrorCode().httpStatus().is5xxServerError()) {
            log.error("[{}] {} - {}", requestId, ex.getErrorCode(), ex.getMessage(), ex);
        } else {
            log.warn("[{}] {} - {}", requestId, ex.getErrorCode(), ex.getMessage());
        }
        return ResponseEntity.status(ex.getErrorCode().httpStatus())
                .body(new ApiResponse<>(null, ApiErrorBody.of(ex.getErrorCode(), ex.getMessage(), requestId)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String requestId = RequestIdFilter.currentOrNew();
        List<ApiErrorBody.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiErrorBody.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(SakarErrorCode.VALIDATION_FAILED.httpStatus())
                .body(new ApiResponse<>(null, ApiErrorBody.validation(requestId, fieldErrors)));
    }

    @ExceptionHandler({ AuthenticationException.class, BadCredentialsException.class })
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(Exception ex) {
        String requestId = RequestIdFilter.currentOrNew();
        return ResponseEntity.status(SakarErrorCode.UNAUTHENTICATED.httpStatus())
                .body(new ApiResponse<>(null, ApiErrorBody.of(SakarErrorCode.UNAUTHENTICATED, "Authentication required", requestId)));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        String requestId = RequestIdFilter.currentOrNew();
        return ResponseEntity.status(SakarErrorCode.FORBIDDEN.httpStatus())
                .body(new ApiResponse<>(null, ApiErrorBody.of(SakarErrorCode.FORBIDDEN, "You are not authorized to perform this action", requestId)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        String requestId = RequestIdFilter.currentOrNew();
        log.error("[{}] Unhandled exception", requestId, ex);
        return ResponseEntity.status(SakarErrorCode.INTERNAL_ERROR.httpStatus())
                .body(new ApiResponse<>(null, ApiErrorBody.of(SakarErrorCode.INTERNAL_ERROR, "An unexpected error occurred", requestId)));
    }
}
