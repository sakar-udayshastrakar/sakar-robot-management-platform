package com.sakarrobotics.c40agent.telemetry;

/**
 * Holds the raw, unparsed String response from a single SDK query
 * (e.g. battery status, motor status). The Peanut SDK v1.3.0 documentation
 * does not publish a confirmed JSON schema for these responses, so this
 * module deliberately does NOT attempt to parse fields out of them - doing
 * so would mean inventing a response shape we have not verified against
 * the physical C40. The raw text is shown as-is in the diagnostic UI.
 */
public final class RawSnapshot {

    private final long timestamp;
    private final boolean success;
    private final String rawResponse;
    private final int errorCode;
    private final String errorMessage;

    private RawSnapshot(long timestamp, boolean success, String rawResponse,
                         int errorCode, String errorMessage) {
        this.timestamp = timestamp;
        this.success = success;
        this.rawResponse = rawResponse;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public static RawSnapshot success(String rawResponse) {
        return new RawSnapshot(System.currentTimeMillis(), true, rawResponse, 0, null);
    }

    public static RawSnapshot error(int errorCode, String errorMessage) {
        return new RawSnapshot(System.currentTimeMillis(), false, null, errorCode, errorMessage);
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return success ? String.valueOf(rawResponse) : ("error " + errorCode + ": " + errorMessage);
    }
}
