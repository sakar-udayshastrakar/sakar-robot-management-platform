package com.sakarrobotics.c40agent.api.mqtt;

import java.util.Map;

/**
 * REAL (non-simulated) executor for the {@code RETURN_TO_DOCK} command
 * (Roadmap Phase 7). Unlike {@code SimulatedRobotCommandExecutor}
 * (`START_TASK`), this class drives the officially-distributed Peanut SDK
 * through {@link ReturnToDockGateway} — never a mock, never a raw/
 * undocumented CoAP call, never Keenon Cloud.
 *
 * <p><strong>Verified SDK contract (Roadmap Phase 7 investigation,
 * independently re-confirmed by {@code javap} against the exact AAR
 * {@code SakarC40Agent} bundles):</strong>
 * <ul>
 *   <li>Class/method: {@code com.keenon.sdk.component.BatteryComponent.autoCharge(IDataCallback, int)}</li>
 *   <li>Internally delegates to {@code com.keenon.sdk.api.ChargeAutoApi.send(IDataCallback, int)},
 *   annotated {@code @CoapCommond(path="/charge/auto")} — the identical local
 *   CoAP endpoint independently found in Keenon's own first-party Peanut
 *   Clean app (see {@code PEANUT_CLEAN_V3.7.6_INTERNAL_OPERATION_ANALYSIS.md}),
 *   cross-confirming this is genuinely the "return to charging dock"
 *   operation and not a misread API.</li>
 *   <li>The {@code int} parameter is a charging-pile id. This project does
 *   not have a confirmed pile id for any specific C40 install and refuses
 *   to guess one (same stance {@code ChargingBridge} already takes for
 *   {@code startCharging}). {@code ChargeAutoApi.CoapParams()}'s own
 *   decompiled bytecode shows {@code pile <= 0} makes it build a
 *   <em>null</em> request body (no {@code "dst"} field at all) rather than
 *   sending an explicit pile number — this is verified, not guessed,
 *   behavior of the exact bundled AAR, and is the value {@code
 *   PeanutSdkBridge.autoCharge()} uses.</li>
 *   <li>Initialization requirement: identical to every other SDK call in
 *   this project — {@code PeanutSDK.init()} must have already succeeded
 *   (see {@code PeanutSdkBridge.init}); this class does not perform
 *   initialization itself.</li>
 *   <li>Safety gating: reached only through {@code C40RobotController.returnToDock()},
 *   which is gated by {@code OperatingMode.HARDWARE_TEST} exactly like
 *   {@code startCharging}/{@code stopCharging} — nothing in this class
 *   bypasses that guard, because this class never calls {@code
 *   C40RobotController} directly, only through the {@link
 *   ReturnToDockGateway} seam whose real implementation does.</li>
 * </ul>
 *
 * <p><strong>What "success" means here — the completion-evidence
 * distinction Roadmap Phase 7 requires:</strong> {@link
 * ReturnToDockGateway.Callback#onAccepted(String)} firing confirms only
 * that the local robot control interface accepted and dispatched the
 * request. No SDK signal confirming the robot has physically arrived at a
 * dock or begun charging was used in this pass (the SDK's documented
 * charge-status codes exist but their exact meanings are not confirmed —
 * see {@code PEANUT_SDK_C40_TECHNICAL_STUDY.md} §4.C — so polling them
 * here would itself be guessing). This is exactly why this class calls
 * {@link RobotCommandResultReporter#reportDispatched(String)} on SDK
 * acceptance, never {@link RobotCommandResultReporter#reportCompleted(String)}.
 */
public final class PeanutSdkReturnToDockExecutor implements RobotCommandExecutor {

    private final ReturnToDockGateway gateway;

    public PeanutSdkReturnToDockExecutor(ReturnToDockGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public void execute(String commandType, Map<String, Object> params, RobotCommandResultReporter reporter) {
        reporter.reportExecuting();
        gateway.autoCharge(new ReturnToDockGateway.Callback() {
            @Override
            public void onAccepted(String rawResponse) {
                reporter.reportDispatched(
                        "Peanut SDK BatteryComponent.autoCharge() accepted the return-to-dock request via the "
                                + "local robot control interface (CoAP /charge/auto, no Keenon Cloud call). This "
                                + "confirms the command was dispatched; it does NOT confirm the robot has "
                                + "physically arrived at a charging dock or begun charging - no SDK completion "
                                + "signal was used in this pass. rawResponse=" + rawResponse);
            }

            @Override
            public void onError(int errorCode, String errorMessage) {
                reporter.reportFailed("Peanut SDK BatteryComponent.autoCharge() failed or was blocked: errorCode="
                        + errorCode + " message=" + errorMessage);
            }
        });
    }
}
