package com.sakarrobotics.c40agent.virtual;

import java.util.Map;
import java.util.UUID;

import com.sakarrobotics.c40agent.api.mqtt.RobotCommandExecutor;
import com.sakarrobotics.c40agent.api.mqtt.RobotCommandResultReporter;

/**
 * {@link RobotCommandExecutor} backed by a {@link VirtualRobotEngine}
 * instead of the real Peanut SDK (Roadmap Phase 9, see
 * ../../VIRTUAL_C40_SIMULATOR.md). This is the SAME interface {@code
 * PeanutSdkGoToPointExecutor}/{@code PeanutSdkReturnToDockExecutor}
 * implement for the real robot - {@link com.sakarrobotics.c40agent.api.mqtt.CommandDispatcher}
 * cannot tell, and does not need to know, which kind of executor it is
 * driving. This is precisely how the simulator reuses the existing
 * command/message contracts instead of creating a second pipeline.
 *
 * <p>Only {@code GO_TO_POINT} and {@code RETURN_TO_DOCK} are wired -
 * {@code START_TASK} and any other command type report {@link
 * RobotCommandResultReporter#reportFailed} with an explicit
 * "not supported by the virtual simulator" message, never a fabricated
 * success.
 */
public final class VirtualRobotCommandExecutor implements RobotCommandExecutor {

    private static final String GO_TO_POINT = "GO_TO_POINT";
    private static final String RETURN_TO_DOCK = "RETURN_TO_DOCK";

    private final VirtualRobotEngine engine;

    public VirtualRobotCommandExecutor(VirtualRobotEngine engine) {
        this.engine = engine;
    }

    @Override
    public void execute(String commandType, Map<String, Object> params, RobotCommandResultReporter reporter) {
        String commandId = UUID.randomUUID().toString();
        if (GO_TO_POINT.equals(commandType)) {
            Integer destinationId = extractDestinationId(params);
            if (destinationId == null) {
                reporter.reportFailed("GO_TO_POINT requires a non-negative integer 'destinationId' in params — "
                        + "none was provided or it was invalid. The virtual simulator never invents a destination id, "
                        + "same rule as the real robot's executor. params=" + params);
                return;
            }
            engine.goToPoint(destinationId, commandId, reporter);
            return;
        }
        if (RETURN_TO_DOCK.equals(commandType)) {
            engine.returnToDock(commandId, reporter);
            return;
        }
        reporter.reportFailed("commandType=" + commandType + " is not supported by the virtual simulator "
                + "(only GO_TO_POINT and RETURN_TO_DOCK are wired) — this is a simulation limitation, "
                + "not a claim about the real robot's capabilities.");
    }

    /**
     * Same destinationId-shape contract as {@code PeanutSdkGoToPointExecutor.extractDestinationId}
     * and the backend's {@code RobotCommandService.validateParams} - intentionally duplicated
     * (not extracted to a shared, cross-module utility) since it is a small, self-contained rule
     * and :virtual-agent must not depend on :api's internal/private helpers.
     */
    private static Integer extractDestinationId(Map<String, Object> params) {
        if (params == null) {
            return null;
        }
        Object raw = params.get("destinationId");
        if (raw == null) {
            return null;
        }
        long value;
        if (raw instanceof Number) {
            value = ((Number) raw).longValue();
        } else {
            try {
                value = Long.parseLong(raw.toString());
            } catch (NumberFormatException notANumber) {
                return null;
            }
        }
        if (value < 0 || value > Integer.MAX_VALUE) {
            return null;
        }
        return (int) value;
    }
}
