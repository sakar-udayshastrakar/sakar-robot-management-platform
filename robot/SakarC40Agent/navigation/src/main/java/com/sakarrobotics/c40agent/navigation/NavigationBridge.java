package com.sakarrobotics.c40agent.navigation;

import com.sakarrobotics.c40agent.sdk.PeanutSdkBridge;
import com.sakarrobotics.c40agent.sdk.SdkCallback;

/**
 * Thin navigation-specific wrapper over {@link PeanutSdkBridge}. Does not
 * decide whether it is safe to move the robot - that gate lives in
 * :robot's C40RobotController (OperatingMode). This class exists purely
 * to keep navigation call sites out of the robot/ui modules.
 *
 * Target-point IDs are never invented here - callers must supply a real,
 * confirmed target id (see "required target-point table" in README.md).
 */
public final class NavigationBridge {

    private final PeanutSdkBridge bridge;

    public NavigationBridge() {
        this(PeanutSdkBridge.getInstance());
    }

    NavigationBridge(PeanutSdkBridge bridge) {
        this.bridge = bridge;
    }

    public void goToPoint(int targetId, SdkCallback callback) {
        bridge.setNavigationTarget(targetId, callback);
    }

    public void pause(SdkCallback callback) {
        bridge.pauseNavigation(callback);
    }

    public void resume(SdkCallback callback) {
        bridge.resumeNavigation(callback);
    }

    public void stop(SdkCallback callback) {
        bridge.stopNavigation(callback);
    }

    public void getStatus(SdkCallback callback) {
        bridge.queryNavigationStatus(callback);
    }
}
