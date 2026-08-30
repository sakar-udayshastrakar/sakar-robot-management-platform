package com.sakarrobotics.c40agent.api.mqtt;

import java.util.Map;

/**
 * Routes a command to the {@link RobotCommandExecutor} registered for its
 * {@code commandType} (Roadmap Phase 7 "RETURN_TO_DOCK") — e.g. {@code
 * START_TASK} to a software simulation, {@code RETURN_TO_DOCK} to a real
 * Peanut SDK executor. This lets {@link CommandDispatcher} stay unchanged
 * (it always talks to exactly one {@link RobotCommandExecutor}), while the
 * app module wires up as many or as few real/simulated executors as it
 * currently has, per command type — "do not create another command
 * pipeline" is satisfied by keeping this a router in front of the existing
 * pipeline, not a parallel one.
 */
public final class CompositeRobotCommandExecutor implements RobotCommandExecutor {

    private final Map<String, RobotCommandExecutor> executorsByCommandType;

    public CompositeRobotCommandExecutor(Map<String, RobotCommandExecutor> executorsByCommandType) {
        this.executorsByCommandType = executorsByCommandType;
    }

    @Override
    public void execute(String commandType, Map<String, Object> params, RobotCommandResultReporter reporter) {
        // commandType == null short-circuits before the map lookup: Map.of()-backed maps (used by
        // every current caller) throw NullPointerException on get(null) rather than returning null.
        RobotCommandExecutor executor = commandType == null ? null : executorsByCommandType.get(commandType);
        if (executor == null) {
            reporter.reportFailed("No executor registered for commandType=" + commandType);
            return;
        }
        executor.execute(commandType, params, reporter);
    }
}
