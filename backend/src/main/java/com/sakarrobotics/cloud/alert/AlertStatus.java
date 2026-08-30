package com.sakarrobotics.cloud.alert;

/** Same free-text-column-backed-by-an-enum-of-constants convention as {@link AlertType}. */
public enum AlertStatus {
    OPEN,
    ACKNOWLEDGED,
    RESOLVED
}
