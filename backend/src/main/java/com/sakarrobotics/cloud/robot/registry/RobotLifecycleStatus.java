package com.sakarrobotics.cloud.robot.registry;

/** Registration lifecycle (distinct from real-time online/offline status — see {@code robot_status}). */
public enum RobotLifecycleStatus {
    REGISTERED,
    ACTIVE,
    DEACTIVATED
}
