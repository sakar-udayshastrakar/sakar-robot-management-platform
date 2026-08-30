package com.sakarrobotics.c40agent;

import com.sakarrobotics.c40agent.api.mqtt.ReturnToDockGateway;
import com.sakarrobotics.c40agent.robot.C40RobotController;
import com.sakarrobotics.c40agent.sdk.SdkCallback;

/**
 * REAL (non-simulated) {@link ReturnToDockGateway}, wrapping {@link
 * C40RobotController#returnToDock} — the only place in this pass where a
 * remote command actually reaches the Peanut SDK / robot control
 * interface (Roadmap Phase 7 "RETURN_TO_DOCK"). {@code
 * C40RobotController.returnToDock} enforces {@code
 * OperatingMode.HARDWARE_TEST} itself; this class does not, and must not,
 * bypass that.
 */
final class RealReturnToDockGateway implements ReturnToDockGateway {

    private final C40RobotController controller;

    RealReturnToDockGateway(C40RobotController controller) {
        this.controller = controller;
    }

    @Override
    public void autoCharge(Callback callback) {
        controller.returnToDock(new SdkCallback() {
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
