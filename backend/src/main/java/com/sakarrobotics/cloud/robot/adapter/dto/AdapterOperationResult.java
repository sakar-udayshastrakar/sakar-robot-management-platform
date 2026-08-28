package com.sakarrobotics.cloud.robot.adapter.dto;

import java.time.Instant;

/**
 * The result of any write/control operation through an adapter
 * (start/stop/pause/resume task, return-to-dock, lock, unlock). Deliberately
 * separates three distinct claims per Master Requirements Part 40's
 * governing rule — "API accepted" is never conflated with "robot physically
 * executed" or "operation verified in history/logs":
 *
 * <ul>
 *   <li>{@link #accepted()} — the vendor/local API returned a success receipt
 *       for the request (e.g. Keenon's {@code 610000}).</li>
 *   <li>{@link #physicallyConfirmed()} — always {@code false} from an
 *       adapter call alone. Only reconciliation against status/logs
 *       (a later, separate step) may ever set this true.</li>
 * </ul>
 */
public record AdapterOperationResult(
        boolean accepted,
        boolean physicallyConfirmed,
        String vendorReference,
        String message,
        Instant respondedAt) {

    public static AdapterOperationResult accepted(String vendorReference, String message) {
        return new AdapterOperationResult(true, false, vendorReference, message, Instant.now());
    }

    public static AdapterOperationResult rejected(String message) {
        return new AdapterOperationResult(false, false, null, message, Instant.now());
    }
}
