package com.sakarrobotics.c40agent.robot;

/**
 * Safety gate for {@link C40RobotController}. There is currently no
 * physical C40 connected to any development environment this project has
 * been built in, so the default and only mode wired into the UI is
 * DIAGNOSTIC_ONLY.
 *
 * HARDWARE_TEST exists as a documented, deliberate opt-in for later,
 * supervised on-robot testing. Nothing in this codebase switches into it
 * automatically, and no UI control exposes it.
 */
public enum OperatingMode {

    /** Only read-only status/diagnostic queries are allowed. */
    DIAGNOSTIC_ONLY,

    /** Navigation, motor and charging actions are allowed. Not used by the current UI. */
    HARDWARE_TEST
}
