package com.sakarrobotics.cloud.robot.registry;

/**
 * Which concrete {@code RobotAdapter} implementation serves a robot model
 * (SAKAR_ROBOT_PLATFORM_ARCHITECTURE.md §8, SAKAR_ROBOT_PLATFORM_DATABASE.md
 * Appendix A.3). {@link #KEENON_CLOUD} is the only adapter with a working
 * implementation as of Phase 1 — it is the current, live-tested,
 * KEENON-CLOUD DEPENDENT integration (Master Requirements Part 10/40).
 * {@link #KEENON_LOCAL_SDK} and {@link #SAKAR_NATIVE} are registered as
 * known future values; their adapters are stubs pending Phase 2+ /
 * physical validation.
 */
public enum AdapterType {
    KEENON_CLOUD,
    KEENON_LOCAL_SDK,
    SAKAR_NATIVE,
    OTHER
}
