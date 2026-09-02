package com.sakarrobotics.c40agent;

import com.sakarrobotics.c40agent.api.mqtt.GoToPointGateway;
import com.sakarrobotics.c40agent.robot.C40RobotController;
import com.sakarrobotics.c40agent.sdk.SdkCallback;

/**
 * REAL (non-simulated) {@link GoToPointGateway}, wrapping {@link
 * C40RobotController#goToPoint} — the only place in this pass where a
 * remote {@code GO_TO_POINT} command actually reaches the Peanut SDK /
 * robot control interface (Roadmap Phase 8, see {@code
 * C40_S_GO_TO_POINT_SDK_INVESTIGATION.md}). {@code
 * C40RobotController.goToPoint} enforces {@code OperatingMode.HARDWARE_TEST}
 * itself; this class does not, and must not, bypass that.
 */
final class RealGoToPointGateway implements GoToPointGateway {

    private final C40RobotController controller;

    RealGoToPointGateway(C40RobotController controller) {
        this.controller = controller;
    }

    @Override
    public void goToPoint(int destinationId, Callback callback) {
        controller.goToPoint(destinationId, new SdkCallback() {
            @Override
            public void onSuccess(String rawResponse) {
                callback.onAccepted(rawResponse);
            }

            @Override
            public void onError(int errorCode, String errorMessage) {
                callback.onError(errorCode, errorMessage);
            }
        });
    }
}
