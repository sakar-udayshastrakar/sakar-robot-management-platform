package com.sakarrobotics.c40agent.virtual;

/**
 * A virtual robot's navigation state (Roadmap Phase 9, see
 * ../../VIRTUAL_C40_SIMULATOR.md). This is a NEW, simulator-only
 * vocabulary - no equivalent enum exists anywhere else in this codebase
 * to collide with or reuse (the real Peanut SDK's {@code
 * NavigationStatusApi} response carries raw, undocumented integer status
 * codes, not a clean enum - see {@code
 * C40_S_GO_TO_POINT_SDK_INVESTIGATION.md} §8 - so this does not
 * "invent conflicting domain terminology", it fills a genuine gap).
 *
 * <p>Matches the deterministic progression the simulator spec asked for:
 * {@code START -> MOVING -> ARRIVED}, plus {@link #IDLE} (before any
 * navigation command) and {@link #BLOCKED} (a deterministic failure
 * state, see {@link VirtualRobotEngine}).
 */
public enum NavigationState {
    IDLE,
    MOVING,
    ARRIVED,
    BLOCKED
}
