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
 *   <li>{@link #vendorContacted()} — a fourth, equally deliberate
 *       distinction (command-dispatch-semantics fix): whether the vendor
 *       actually received this request at all. A local, pre-vendor-call
 *       rejection (e.g. no area specified) never contacts the vendor —
 *       {@code RobotCommandService}/callers must not report such a case as
 *       "dispatched" just because it produced a non-{@code accepted}
 *       result. Only {@link #accepted()} or {@link #rejectedByVendor} ever
 *       set this {@code true}; {@link #rejected} (a pre-vendor-call
 *       rejection) sets it {@code false}.</li>
 * </ul>
 */
public record AdapterOperationResult(
        boolean accepted,
        boolean physicallyConfirmed,
        boolean vendorContacted,
        String vendorReference,
        String message,
        Instant respondedAt) {

    public static AdapterOperationResult accepted(String vendorReference, String message) {
        return new AdapterOperationResult(true, false, true, vendorReference, message, Instant.now());
    }

    /** A pre-vendor-call rejection — the adapter never contacted the vendor at all (e.g. no area specified). */
    public static AdapterOperationResult rejected(String message) {
        return new AdapterOperationResult(false, false, false, null, message, Instant.now());
    }

    /** The vendor was contacted and gave a definitive, non-accepted response (e.g. a non-610000 Keenon code). */
    public static AdapterOperationResult rejectedByVendor(String message) {
        return new AdapterOperationResult(false, false, true, null, message, Instant.now());
    }
}
