package com.sakarrobotics.c40agent.virtual;

/**
 * Thrown by {@link VirtualRobotEngine#getAllDestinations()} when the
 * virtual robot is offline - a real robot cannot be queried at all while
 * unreachable, so the simulator does not pretend otherwise (Roadmap
 * Phase 9, Phase 8 "offline").
 */
public final class VirtualRobotOfflineException extends RuntimeException {

    public VirtualRobotOfflineException(String robotId) {
        super("Virtual robot " + robotId + " is OFFLINE - cannot query destinations.");
    }
}
