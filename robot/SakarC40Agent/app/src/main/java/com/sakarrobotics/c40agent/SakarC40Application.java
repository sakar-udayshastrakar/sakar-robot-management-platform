package com.sakarrobotics.c40agent;

import android.app.Application;

import com.sakarrobotics.c40agent.api.mqtt.AgentMqttClient;
import com.sakarrobotics.c40agent.api.mqtt.HeartbeatScheduler;
import com.sakarrobotics.c40agent.api.mqtt.SakarMqttConfig;
import com.sakarrobotics.c40agent.api.mqtt.TelemetryScheduler;
import com.sakarrobotics.c40agent.logging.SdkCallLogger;
import com.sakarrobotics.c40agent.robot.C40RobotController;
import com.sakarrobotics.c40agent.robot.C40RobotControllerHolder;
import com.sakarrobotics.c40agent.sdk.SdkConnectionConfig;

/**
 * Owns the single, application-scoped {@link C40RobotController} instance,
 * and (Phase 3) the single {@link AgentMqttClient} connecting this agent
 * to the Sakar Cloud backend. Activities must obtain the controller from
 * here rather than constructing their own - the Peanut SDK is a
 * process-wide singleton underneath, so more than one controller instance
 * would fight over the same connection.
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
     * Phase 3 (Robot Communication / MQTT). No-op if
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
        mqttClient = new AgentMqttClient(mqttConfig, null);
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
