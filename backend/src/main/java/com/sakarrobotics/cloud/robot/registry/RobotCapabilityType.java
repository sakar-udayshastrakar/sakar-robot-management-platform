package com.sakarrobotics.cloud.robot.registry;

/**
 * The generic robot capability vocabulary the Robot Adapter Layer speaks
 * (Master Requirements §6.A, SAKAR_ROBOT_PLATFORM_ARCHITECTURE.md §8).
 * Never assume a robot supports all of these — see {@link RobotCapability}.
 */
public enum RobotCapabilityType {
    GET_STATUS,
    GET_BATTERY,
    GET_TELEMETRY,
    GET_EVENTS,
    START_TASK,
    STOP_TASK,
    PAUSE_TASK,
    RESUME_TASK,
    RETURN_TO_DOCK,
    LOCK,
    UNLOCK,
    GET_MAP,
    GET_AREAS,
    CLEANING
}
