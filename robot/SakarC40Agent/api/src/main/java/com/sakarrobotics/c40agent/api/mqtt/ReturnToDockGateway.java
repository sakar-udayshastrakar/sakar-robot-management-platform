package com.sakarrobotics.c40agent.api.mqtt;

/**
 * The narrow seam {@link PeanutSdkReturnToDockExecutor} depends on instead
 * of the concrete Android/Peanut-SDK-dependent types (Roadmap Phase 7
 * "RETURN_TO_DOCK"). This interface lives in {@code :api} (plain Java,
 * unit-testable with a fake); the real implementation, wrapping {@code
 * C40RobotController}/{@code ChargingBridge}/{@code PeanutSdkBridge}, is
 * supplied by the {@code app} module (the composition root) — the same
 * "interface here, real implementation in {@code app}" pattern already
 * used by {@link TelemetrySnapshotProvider} and {@link RobotCommandExecutor}.
 *
 * <p>This interface intentionally exposes nothing Keenon/Peanut-specific
 * beyond its name and Javadoc — no {@code com.keenon.*} type appears in
 * its signature, preserving the existing rule that only {@code :sdk}
 * (specifically {@code PeanutSdkBridge}) may import those types.
 */
public interface ReturnToDockGateway {

    /**
     * Requests that the robot return to a charging dock, via the local
     * Peanut SDK (never Keenon Cloud). Implementations must not guess a
     * specific charging-pile identifier that has not been confirmed for
     * the target robot — see {@link PeanutSdkReturnToDockExecutor}'s
     * Javadoc for the verified, non-guessed default this project uses.
     */
    void autoCharge(Callback callback);

    /** Result of a single {@link #autoCharge(Callback)} call. */
    interface Callback {

        /**
         * The local robot control interface accepted and dispatched the
         * request. This does NOT mean the robot has physically arrived at
         * a dock or begun charging — see {@link PeanutSdkReturnToDockExecutor}.
         */
        void onAccepted(String rawResponse);

        /** The local robot control interface rejected the request or errored. */
        void onError(int errorCode, String errorMessage);
    }
}
