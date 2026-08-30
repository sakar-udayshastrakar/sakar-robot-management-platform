package com.sakarrobotics.c40agent.api.mqtt;

import java.util.Map;

/**
 * Executes a robot command received over MQTT (Roadmap Phase 6/7 "Robot
 * Agent Command Loop"). Implemented by the {@code app} module (the
 * composition root) and injected into {@link CommandDispatcher} — this
 * follows the exact same decoupling pattern as {@link
 * TelemetrySnapshotProvider}: this module ({@code :api}) depends on this
 * interface only, never on {@code :robot}/{@code :sdk} directly.
 *
 * <p>Implementations MUST report every lifecycle transition through the
 * given {@link RobotCommandResultReporter} and must never report {@link
 * RobotCommandResultReporter#reportCompleted(String)} unless the command
 * actually finished — an MQTT message being received is not the same
 * thing as a robot completing it, and this interface exists specifically
 * so that distinction is never silently collapsed.
 */
public interface RobotCommandExecutor {

    void execute(String commandType, Map<String, Object> params, RobotCommandResultReporter reporter);
}
