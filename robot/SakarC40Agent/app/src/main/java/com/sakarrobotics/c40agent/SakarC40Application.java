package com.sakarrobotics.c40agent;

import java.util.HashMap;
import java.util.Map;

import android.app.Application;

import com.sakarrobotics.c40agent.api.mqtt.AgentMqttClient;
import com.sakarrobotics.c40agent.api.mqtt.CommandDispatcher;
import com.sakarrobotics.c40agent.api.mqtt.CompositeRobotCommandExecutor;
import com.sakarrobotics.c40agent.api.mqtt.HeartbeatScheduler;
import com.sakarrobotics.c40agent.api.mqtt.PeanutSdkGoToPointExecutor;
import com.sakarrobotics.c40agent.api.mqtt.PeanutSdkReturnToDockExecutor;
import com.sakarrobotics.c40agent.api.mqtt.RobotCommandExecutor;
import com.sakarrobotics.c40agent.api.mqtt.SakarMqttConfig;
import com.sakarrobotics.c40agent.api.mqtt.TelemetryScheduler;
import com.sakarrobotics.c40agent.logging.SdkCallLogger;
import com.sakarrobotics.c40agent.robot.C40RobotController;
import com.sakarrobotics.c40agent.robot.C40RobotControllerHolder;
import com.sakarrobotics.c40agent.sdk.SdkConnectionConfig;

/**
 * Owns the single, application-scoped {@link C40RobotController} instance,
 * and (Phase 3) the single {@link AgentMqttClient} connecting this agent
 * to the Sakar Cloud backend — including (Phase 6/7) the {@link
 * CommandDispatcher} that consumes inbound robot commands. Activities must
 * obtain the controller from here rather than constructing their own - the
 * Peanut SDK is a process-wide singleton underneath, so more than one
 * controller instance would fight over the same connection.
 */
public class SakarC40Application extends Application {

    private C40RobotController controller;
    private AgentMqttClient mqttClient;
    private HeartbeatScheduler heartbeatScheduler;
    private TelemetryScheduler telemetryScheduler;

    @Override
    public void onCreate() {
        super.onCreate();
        SdkConnectionConfig config = SdkConnectionConfig.fromBuildConfig();
        controller = new C40RobotController(this, config);
        // OperatingMode defaults to DIAGNOSTIC_ONLY and is never changed here.
        C40RobotControllerHolder.set(controller);

        startMqttIfConfigured();
    }

    /**
     * Phase 3 (Robot Communication / MQTT), extended in Phase 6/7 with
     * inbound command consumption via {@link CommandDispatcher}. No-op if
     * {@code secrets.properties} does not configure a broker URL and robot
     * identity - same "compiles and runs with nothing configured" fallback
     * {@link SdkConnectionConfig} already provides for the Peanut SDK
     * credentials, so this diagnostic build keeps working with no MQTT
     * broker present.
     */
    private void startMqttIfConfigured() {
        SakarMqttConfig mqttConfig = SakarMqttConfigFactory.fromBuildConfig();
        if (!mqttConfig.isConfigured()) {
            SdkCallLogger.getInstance().logError("SakarC40Application.startMqttIfConfigured", "n/a", -1,
                    "MQTT not configured (secrets.properties) - agent stays diagnostic-only, no Sakar Cloud link");
            return;
        }
        // Roadmap Phase 6/7 "Robot Agent Command Loop": START_TASK has no supported Peanut SDK
        // cleaning-control API (see SimulatedRobotCommandExecutor's own Javadoc), so it stays a
        // software placeholder. RETURN_TO_DOCK (Phase 7) DOES have a verified official SDK
        // API - BatteryComponent.autoCharge() - so it gets a real executor, gated by the
        // existing C40RobotController.returnToDock()'s OperatingMode.HARDWARE_TEST check.
        // GO_TO_POINT (Roadmap Phase 8, see C40_S_GO_TO_POINT_SDK_INVESTIGATION.md) DOES have a
        // verified official SDK API too - NavigationComponent.setTarget(IDataCallback, int) - so
        // it also gets a real executor, gated the same way via C40RobotController.goToPoint().
        // The int is a pre-registered destination id, never invented here - see
        // PeanutSdkGoToPointExecutor's Javadoc for where a command must supply it from.
        Map<String, RobotCommandExecutor> executorsByCommandType = new HashMap<>();
        executorsByCommandType.put("START_TASK", new SimulatedRobotCommandExecutor());
        executorsByCommandType.put("RETURN_TO_DOCK",
                new PeanutSdkReturnToDockExecutor(new RealReturnToDockGateway(controller)));
        executorsByCommandType.put("GO_TO_POINT",
                new PeanutSdkGoToPointExecutor(new RealGoToPointGateway(controller)));
        CommandDispatcher commandDispatcher = new CommandDispatcher(new CompositeRobotCommandExecutor(executorsByCommandType));
        mqttClient = new AgentMqttClient(mqttConfig, commandDispatcher);
        commandDispatcher.attachResultPublisher(mqttClient::publishEvent);
        mqttClient.connect();

        heartbeatScheduler = new HeartbeatScheduler(mqttClient, BuildConfig.VERSION_NAME);
        heartbeatScheduler.start(mqttConfig.getHeartbeatIntervalSeconds());

        telemetryScheduler = new TelemetryScheduler(mqttClient, new C40TelemetrySnapshotProvider(controller));
        telemetryScheduler.start(mqttConfig.getTelemetryIntervalSeconds());
    }

    public C40RobotController getController() {
        return controller;
    }

    /**
     * Never called by the Android framework on a real device (only in
     * some emulator scenarios) - present so the MQTT link and its
     * schedulers can be torn down cleanly if it ever does fire.
     */
    @Override
    public void onTerminate() {
        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdown();
        }
        if (telemetryScheduler != null) {
            telemetryScheduler.shutdown();
        }
        if (mqttClient != null) {
            mqttClient.disconnect();
        }
        super.onTerminate();
    }
}
