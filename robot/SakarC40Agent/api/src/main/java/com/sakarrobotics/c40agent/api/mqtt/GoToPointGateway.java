package com.sakarrobotics.c40agent.api.mqtt;

/**
 * The narrow seam {@link PeanutSdkGoToPointExecutor} depends on instead of
 * the concrete Android/Peanut-SDK-dependent types (Roadmap Phase 8
 * "GO_TO_POINT", see {@code C40_S_GO_TO_POINT_SDK_INVESTIGATION.md}). This
 * interface lives in {@code :api} (plain Java, unit-testable with a fake);
 * the real implementation, wrapping {@code C40RobotController}/{@code
 * NavigationBridge}/{@code PeanutSdkBridge}, is supplied by the {@code app}
 * module (the composition root) — the same "interface here, real
 * implementation in {@code app}" pattern already used by {@link
 * ReturnToDockGateway} and {@link TelemetrySnapshotProvider}.
 *
 * <p>This interface intentionally exposes nothing Keenon/Peanut-specific
 * beyond its name and Javadoc — no {@code com.keenon.*} type appears in its
 * signature, preserving the existing rule that only {@code :sdk}
 * (specifically {@code PeanutSdkBridge}) may import those types.
 */
public interface GoToPointGateway {

    /**
     * Requests that the robot navigate to a pre-registered destination id
     * on its own currently-loaded map, via the local Peanut SDK (never
     * Keenon Cloud, never raw CoAP, never rosbridge). Implementations must
     * not invent or default {@code destinationId} — see {@link
     * PeanutSdkGoToPointExecutor}'s Javadoc for where that id must come
     * from.
     */
    void goToPoint(int destinationId, Callback callback);

    /** Result of a single {@link #goToPoint(int, Callback)} call. */
    interface Callback {

        /**
         * The local robot control interface accepted and dispatched the
         * request. This does NOT mean the robot has physically reached the
         * destination — see {@link PeanutSdkGoToPointExecutor}.
         */
        void onAccepted(String rawResponse);

        /** The local robot control interface rejected the request or errored. */
        void onError(int errorCode, String errorMessage);
    }
}
