package com.sakarrobotics.c40agent.api.mqtt;

import java.util.Map;

/**
 * REAL (non-simulated) executor for the {@code GO_TO_POINT} command
 * (Roadmap Phase 8). Like {@code PeanutSdkReturnToDockExecutor}, this
 * drives the officially-distributed Peanut SDK through {@link
 * GoToPointGateway} — never a mock, never a raw/undocumented CoAP call,
 * never rosbridge, never Keenon Cloud.
 *
 * <p><strong>Verified SDK contract (see {@code
 * C40_S_GO_TO_POINT_SDK_INVESTIGATION.md} for the full evidence trail,
 * independently re-confirmed by {@code javap} against the exact AAR
 * SakarC40Agent bundles):</strong>
 * <ul>
 *   <li>Class/method: {@code com.keenon.sdk.component.NavigationComponent.setTarget(IDataCallback, int)}</li>
 *   <li>Internally delegates to {@code com.keenon.sdk.api.NavigationSetTargetApi.send(IDataCallback, int)},
 *   annotated {@code @CoapCommond(path="/navigation/dst", requestType=POST)} — the identical
 *   local CoAP endpoint independently found in Keenon's own C40 S apps
 *   (Robot Installation Assistant's {@code RobotManager.navigation()}
 *   actively calls this exact path in production).</li>
 *   <li>The {@code int} parameter is a <strong>pre-registered destination
 *   id already known to the robot</strong> — not a raw (x, y) coordinate.
 *   This project has no confirmed destination id for any specific C40
 *   install and refuses to invent one (same stance {@code
 *   ReturnToDockGateway}/{@code ChargingBridge} already take for
 *   charging-pile ids). {@link #execute} rejects any command whose {@code
 *   params} do not carry a non-negative integer {@code destinationId}
 *   rather than guessing one.</li>
 *   <li>Safety gating: reached only through {@code C40RobotController.goToPoint(int, SdkCallback)},
 *   which is gated by {@code OperatingMode.HARDWARE_TEST} exactly like
 *   {@code returnToDock}/{@code startCharging} — nothing in this class
 *   bypasses that guard, because this class never calls {@code
 *   C40RobotController} directly, only through the {@link
 *   GoToPointGateway} seam whose real implementation does.</li>
 * </ul>
 *
 * <p><strong>What "success" means here — the same completion-evidence
 * distinction {@code PeanutSdkReturnToDockExecutor} documents:</strong>
 * {@link GoToPointGateway.Callback#onAccepted(String)} firing confirms only
 * that the local robot control interface accepted and dispatched the
 * request. No SDK signal confirming the robot has physically reached the
 * destination was used in this pass ({@code NavigationStatusApi}'s
 * {@code status}/{@code schedule} codes exist but their exact meanings are
 * not confirmed — polling them here would itself be guessing). This is
 * exactly why this class calls {@link RobotCommandResultReporter#reportDispatched(String)}
 * on SDK acceptance, never {@link RobotCommandResultReporter#reportCompleted(String)}.
 *
 * <p><strong>Missing/invalid {@code destinationId} is reported as {@link
 * RobotCommandResultReporter#reportFailed(String) FAILED} without ever
 * calling {@link RobotCommandResultReporter#reportExecuting()}</strong> —
 * deliberately, since no actual execution attempt is made in that case
 * (the same "never claim work started that didn't" reasoning {@link
 * CommandDispatcher} already applies to an already-expired command).
 */
public final class PeanutSdkGoToPointExecutor implements RobotCommandExecutor {

    private final GoToPointGateway gateway;

    public PeanutSdkGoToPointExecutor(GoToPointGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public void execute(String commandType, Map<String, Object> params, RobotCommandResultReporter reporter) {
        Integer destinationId = extractDestinationId(params);
        if (destinationId == null) {
            reporter.reportFailed("GO_TO_POINT requires a non-negative integer 'destinationId' in params — "
                    + "none was provided, or it was not a valid integer. This executor never invents a "
                    + "destination id. params=" + params);
            return;
        }

        reporter.reportExecuting();
        gateway.goToPoint(destinationId, new GoToPointGateway.Callback() {
            @Override
            public void onAccepted(String rawResponse) {
                reporter.reportDispatched(
                        "Peanut SDK NavigationComponent.setTarget() accepted the go-to-point request via the "
                                + "local robot control interface (CoAP /navigation/dst, destinationId=" + destinationId
                                + "). This confirms the command was dispatched; it does NOT confirm the robot has "
                                + "physically reached the destination - no SDK completion signal was used in this "
                                + "pass. rawResponse=" + rawResponse);
            }

            @Override
            public void onError(int errorCode, String errorMessage) {
                reporter.reportFailed("Peanut SDK NavigationComponent.setTarget() failed or was blocked: errorCode="
                        + errorCode + " message=" + errorMessage);
            }
        });
    }

    /**
     * Same destinationId-shape contract as the backend's own {@code
     * RobotCommandService.validateParams} (Roadmap Phase 8) — this
     * duplication is intentional defense-in-depth, not the "second
     * validation framework" the task asked not to invent: it's the exact
     * same rule (non-negative 32-bit integer), re-checked here because this
     * is the one place a loosely-typed MQTT {@code Map<String, Object>}
     * first becomes a concrete value this project passes to the SDK.
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
