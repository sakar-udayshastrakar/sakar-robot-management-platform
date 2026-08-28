package com.sakarrobotics.cloud.common.error;

import lombok.Getter;

/**
 * Every intentional, client-facing failure in the platform is thrown as
 * (a subclass of) this — never a raw exception message leaked straight to
 * the client, and never a vendor error surfaced verbatim
 * (SAKAR_SECURITY_REQUIREMENTS.md §13.A).
 */
@Getter
public class ApiException extends RuntimeException {

    private final SakarErrorCode errorCode;

    public ApiException(SakarErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ApiException(SakarErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
