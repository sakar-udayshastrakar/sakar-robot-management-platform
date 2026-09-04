package com.sakarrobotics.cloud.common.error;

import org.springframework.http.HttpStatus;

/**
 * The platform's own, stable error vocabulary. This — not an HTTP status
 * code and never a raw vendor (Keenon) code — is the public API contract
 * clients program against (SAKAR_ROBOT_PLATFORM_API_SPEC.md §1.12,
 * Master Requirements Part 14).
 */
public enum SakarErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
    TOKEN_REVOKED(HttpStatus.UNAUTHORIZED),
    STEP_UP_REQUIRED(HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED(HttpStatus.FORBIDDEN),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    TENANT_ACCESS_DENIED(HttpStatus.FORBIDDEN),

    ORGANIZATION_NOT_FOUND(HttpStatus.NOT_FOUND),
    SITE_NOT_FOUND(HttpStatus.NOT_FOUND),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND),
    ROLE_NOT_FOUND(HttpStatus.NOT_FOUND),
    ROBOT_NOT_FOUND(HttpStatus.NOT_FOUND),
    ROBOT_NOT_ACCESSIBLE(HttpStatus.NOT_FOUND),
    ROBOT_MODEL_NOT_FOUND(HttpStatus.NOT_FOUND),
    TASK_NOT_FOUND(HttpStatus.NOT_FOUND),
    ALERT_NOT_FOUND(HttpStatus.NOT_FOUND),
    COMMAND_NOT_FOUND(HttpStatus.NOT_FOUND),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),

    CONFLICT(HttpStatus.CONFLICT),
    DUPLICATE_SERIAL_NUMBER(HttpStatus.CONFLICT),
    DUPLICATE_EXTERNAL_ROBOT_ID(HttpStatus.CONFLICT),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT),
    INVALID_TASK_TRANSITION(HttpStatus.CONFLICT),

    UNSUPPORTED_CAPABILITY(HttpStatus.UNPROCESSABLE_ENTITY),
    EXTERNAL_ROBOT_ID_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY),
    ROBOT_OFFLINE(HttpStatus.CONFLICT),
    ROBOT_NOT_READY(HttpStatus.CONFLICT),
    ROBOT_NOT_IDLE(HttpStatus.CONFLICT),

    COMMAND_EXPIRED(HttpStatus.CONFLICT),
    COMMAND_REPLAY(HttpStatus.CONFLICT),
    COMMAND_DUPLICATE(HttpStatus.CONFLICT),
    COMMAND_UNAUTHORIZED(HttpStatus.FORBIDDEN),
    COMMAND_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT),

    VENDOR_API_ERROR(HttpStatus.BAD_GATEWAY),
    INTEGRATION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),

    FEATURE_NOT_YET_IMPLEMENTED(HttpStatus.NOT_IMPLEMENTED),

    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus httpStatus;

    SakarErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
