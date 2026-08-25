package com.sakarrobotics.c40agent;

import android.app.Application;

import com.sakarrobotics.c40agent.robot.C40RobotController;
import com.sakarrobotics.c40agent.robot.C40RobotControllerHolder;
import com.sakarrobotics.c40agent.sdk.SdkConnectionConfig;

/**
 * Owns the single, application-scoped {@link C40RobotController} instance.
 * Activities must obtain the controller from here rather than constructing
 * their own - the Peanut SDK is a process-wide singleton underneath, so
 * more than one controller instance would fight over the same connection.
 */
public class SakarC40Application extends Application {

    private C40RobotController controller;

    @Override
    public void onCreate() {
        super.onCreate();
        SdkConnectionConfig config = SdkConnectionConfig.fromBuildConfig();
        controller = new C40RobotController(this, config);
        // OperatingMode defaults to DIAGNOSTIC_ONLY and is never changed here.
        C40RobotControllerHolder.set(controller);
    }

    public C40RobotController getController() {
        return controller;
    }
}
