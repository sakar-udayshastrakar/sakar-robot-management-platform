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

    public void stopCharging(SdkCallback callback) {
        bridge.stopCharge(callback);
    }

    public void getBatteryStatus(SdkCallback callback) {
        bridge.queryBatteryStatus(callback);
    }
}
