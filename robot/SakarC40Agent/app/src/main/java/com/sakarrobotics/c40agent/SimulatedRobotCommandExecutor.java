package com.sakarrobotics.c40agent;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.sakarrobotics.c40agent.api.mqtt.RobotCommandExecutor;
import com.sakarrobotics.c40agent.api.mqtt.RobotCommandResultReporter;

/**
 * SOFTWARE SIMULATION ONLY (Roadmap Phase 6/7 "Robot Agent Command Loop").
 * This class never calls {@code PeanutSdkBridge}/{@code C40RobotController}
 * and never touches the physical robot in any way.
 *
 * <p><strong>Why this exists instead of a real executor:</strong> the
 * officially-distributed Peanut SDK v1.3.0 AAR this app bundles (see
 * {@code sdk/libs/peanut-sdk-release.aar}) has no cleaning-control API at
 * all — no {@code CleanComponent}, and no {@code com.keenon.sdk.robot.api}
 * / {@code com.keenon.sdk.coapapi} packages anywhere in the AAR (confirmed
 * by direct decompilation: {@code javap -p} on every class in the AAR
 * lists {@code PeanutSDK}'s complete accessor set as {@code battery(),
 * device(), disinfect(), door(), elevator(), map(), motor(), navigation(),
 * robot(), runtime(), schedule(), update(), vendor(), deviceNode()} — no
 * {@code clean()}). See {@code PEANUT_SDK_C40_API_MATRIX.md} and {@code
 * PEANUT_CLEAN_V3.7.6_INTERNAL_OPERATION_ANALYSIS.md} for the full
 * evidence trail. Cleaning start/pause/resume/stop exists only inside
 * Keenon's own first-party "Peanut Clean" app, reached only via
 * undocumented internal CoAP endpoints ({@code /clean/dst}, {@code
 * /clean/action}) that this SDK does not expose.
 *
 * <p>This class exists so the backend&lt;-&gt;agent command loop's software
 * plumbing (MQTT dispatch, correlation, idempotency, lifecycle result
 * reporting) can be built and verified end-to-end without either (a)
 * fabricating a false "it worked" signal from a real robot call that does
 * not exist, or (b) silently reaching around the Peanut SDK into
 * undocumented internal robot endpoints — that second option is a
 * business/legal decision for Sakar to make explicitly (vendor engagement
 * with Keenon, or a deliberate reverse-engineered-API risk acceptance),
 * not something to default into here.
 *
 * <p>Replace this with a real {@link RobotCommandExecutor} only after that
 * decision is made, and — for any command type that would actually move
 * the robot or change its physical state — only after {@link
 * com.sakarrobotics.c40agent.robot.OperatingMode#HARDWARE_TEST} has been
 * deliberately enabled under the supervised conditions in this project's
 * README ("C40 hardware test procedure"). {@code C40RobotController}'s
 * existing {@code guard()} already blocks every actuation method outside
 * that mode — nothing here bypasses it, because nothing here calls
 * {@code C40RobotController} at all.
 */
final class SimulatedRobotCommandExecutor implements RobotCommandExecutor {

    private static final long SIMULATED_EXECUTION_MILLIS = 2000L;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sakar-command-sim");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public void execute(String commandType, Map<String, Object> params, RobotCommandResultReporter reporter) {
        reporter.reportExecuting();
        executor.schedule(() -> reporter.reportCompleted(
                        "SIMULATED — no Peanut SDK call was made and no physical robot was contacted. "
                                + "commandType=" + commandType + " has no supported Peanut SDK API "
                                + "(see PEANUT_SDK_C40_API_MATRIX.md). This result is a software placeholder only."),
                SIMULATED_EXECUTION_MILLIS, TimeUnit.MILLISECONDS);
    }
}
