package com.sakarrobotics.c40agent.robot;

/**
 * Process-wide access point for the single {@link C40RobotController}
 * created by SakarC40Application. Activities (in :ui) read the controller
 * from here instead of constructing their own - the Peanut SDK is a
 * process-wide singleton underneath, so a second controller instance
 * would fight over the same connection.
 */
public final class C40RobotControllerHolder {

    private static volatile C40RobotController instance;

    public static void set(C40RobotController controller) {
        instance = controller;
    }

    public static C40RobotController get() {
        return instance;
    }

    private C40RobotControllerHolder() {
    }
}
