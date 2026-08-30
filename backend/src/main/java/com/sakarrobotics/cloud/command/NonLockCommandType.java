package com.sakarrobotics.cloud.command;

import com.sakarrobotics.cloud.robot.registry.RobotCapabilityType;

/**
 * The only command types {@link RobotCommandController} accepts (Roadmap
 * Phase 6 "Remote Commands (non-lock)" — {@code LOCK}/{@code UNLOCK} are
 * deliberately excluded here and have no endpoint anywhere in this
 * codebase; see Master Requirements Part 11/38 and Roadmap Phase 7, which
 * gate lock/unlock on physical validation that has not happened).
 */
public enum NonLockCommandType {
    START_TASK(RobotCapabilityType.START_TASK),
    STOP_TASK(RobotCapabilityType.STOP_TASK),
    PAUSE_TASK(RobotCapabilityType.PAUSE_TASK),
    RESUME_TASK(RobotCapabilityType.RESUME_TASK),
    RETURN_TO_DOCK(RobotCapabilityType.RETURN_TO_DOCK);

    private final RobotCapabilityType requiredCapability;

    NonLockCommandType(RobotCapabilityType requiredCapability) {
        this.requiredCapability = requiredCapability;
    }

    public RobotCapabilityType requiredCapability() {
        return requiredCapability;
    }
}
