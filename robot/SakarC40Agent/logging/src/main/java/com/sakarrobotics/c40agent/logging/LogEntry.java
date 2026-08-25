package com.sakarrobotics.c40agent.logging;

/**
 * One record of a single Peanut SDK call, for the on-screen raw SDK log
 * and any future export/diagnostics tooling.
 */
public final class LogEntry {

    private final long timestamp;
    private final String api;
    private final String request;
    private final String response;
    private final boolean success;
    private final int errorCode;
    private final String errorMessage;

    public LogEntry(long timestamp, String api, String request, String response,
                     boolean success, int errorCode, String errorMessage) {
        this.timestamp = timestamp;
        this.api = api;
        this.request = request;
        this.response = response;
        this.success = success;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getApi() {
        return api;
    }

    public String getRequest() {
        return request;
    }

    public String getResponse() {
        return response;
    }

    public boolean isSuccess() {
        return success;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        if (success) {
            return timestamp + " " + api + " request=" + request + " response=" + response + " OK";
        }
        return timestamp + " " + api + " request=" + request
                + " ERROR code=" + errorCode + " message=" + errorMessage;
    }
}
