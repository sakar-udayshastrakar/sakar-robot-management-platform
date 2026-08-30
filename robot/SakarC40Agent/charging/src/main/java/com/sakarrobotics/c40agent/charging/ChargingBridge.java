package com.sakarrobotics.c40agent.charging;

import com.sakarrobotics.c40agent.sdk.PeanutSdkBridge;
import com.sakarrobotics.c40agent.sdk.SdkCallback;

/**
 * Thin charging-specific wrapper over {@link PeanutSdkBridge}. Does not
 * decide whether it is safe to charge - that gate lives in :robot's
 * C40RobotController (OperatingMode).
 *
 * startCharging() maps to BatteryComponent.manualCharge() rather than
 * autoCharge(pile), because autoCharge requires a charging-pile number we
 * have not confirmed for the C40 and refuse to guess (see
 * COMPATIBILITY_REPORT.md).
 */
public final class ChargingBridge {

    private final PeanutSdkBridge bridge;

    public ChargingBridge() {
        this(PeanutSdkBridge.getInstance());
    }

    ChargingBridge(PeanutSdkBridge bridge) {
        this.bridge = bridge;
    }

    public void startCharging(SdkCallback callback) {
        bridge.startManualCharge(callback);
    }

    /**
     * Roadmap Phase 7 "RETURN_TO_DOCK" - unlike {@link #startCharging}, this
     * does use {@code BatteryComponent.autoCharge()} (via {@link
     * PeanutSdkBridge#autoCharge}), because "return to dock" specifically
     * means commanding the robot to navigate back and charge, not just
     * starting a charge cycle assuming it is already docked. See {@code
     * PeanutSdkBridge.autoCharge}'s Javadoc for why pile {@code 0} is used
     * rather than a guessed pile id.
     */
    public void returnToDock(SdkCallback callback) {
        bridge.autoCharge(callback);
    }

    public void stopCharging(SdkCallback callback) {
        bridge.stopCharge(callback);
    }

    public void getBatteryStatus(SdkCallback callback) {
        bridge.queryBatteryStatus(callback);
    }
}
