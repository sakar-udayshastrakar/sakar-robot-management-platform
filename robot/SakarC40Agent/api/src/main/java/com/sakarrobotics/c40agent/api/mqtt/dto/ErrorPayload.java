package com.sakarrobotics.c40agent.api.mqtt.dto;

public final class ErrorPayload {

    private final String errorCode;
    private final String severity;
    private final String source;
    private final String message;
    private final String sdkApi;
    private final String occurredAt;

    public ErrorPayload(String errorCode, String severity, String source, String message, String sdkApi, String occurredAt) {
        this.errorCode = errorCode;
        this.severity = severity;
        this.source = source;
        this.message = message;
        this.sdkApi = sdkApi;
        this.occurredAt = occurredAt;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getSeverity() {
        return severity;
    }

    public String getSource() {
        return source;
    }

    public String getMessage() {
        return message;
    }

    public String getSdkApi() {
        return sdkApi;
    }

    public String getOccurredAt() {
        return occurredAt;
    }
}
