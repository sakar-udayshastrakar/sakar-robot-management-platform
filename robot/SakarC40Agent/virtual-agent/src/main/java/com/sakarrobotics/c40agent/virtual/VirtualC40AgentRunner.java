package com.sakarrobotics.c40agent.virtual;

import java.util.HashMap;
import java.util.Map;

import com.sakarrobotics.c40agent.api.mqtt.AgentMqttClient;
import com.sakarrobotics.c40agent.api.mqtt.CommandDispatcher;
import com.sakarrobotics.c40agent.api.mqtt.CompositeRobotCommandExecutor;
import com.sakarrobotics.c40agent.api.mqtt.HeartbeatScheduler;
import com.sakarrobotics.c40agent.api.mqtt.RobotCommandExecutor;
import com.sakarrobotics.c40agent.api.mqtt.SakarMqttConfig;
import com.sakarrobotics.c40agent.api.mqtt.TelemetryScheduler;

/**
 * Wires ONE {@link VirtualRobotEngine} to the Sakar Cloud backend's
 * EXISTING MQTT command/telemetry contracts (Roadmap Phase 9, see
 * ../../VIRTUAL_C40_SIMULATOR.md) - the virtual-fleet equivalent of
 * {@code SakarC40Application.startMqttIfConfigured()}, minus everything
 * that class does with {@code C40RobotController}/the real Peanut SDK.
 *
 * <p><strong>Connects only to the Sakar MQTT broker supplied by the
 * caller's own {@link SakarMqttConfig}</strong> - the exact same
 * "no-op unless configured" fallback {@code AgentMqttClient.connect()}
 * already has if {@code config.isConfigured()} is false. This class
 * contains no robot IP, no CoAP URL, no rosbridge URL, and no Keenon
 * Cloud domain anywhere - see {@code VirtualAgentRealSdkIsolationTest}.
 * Run one instance per virtual robot (see {@link VirtualRobotRegistry}).
 */
public final class VirtualC40AgentRunner {

    private final VirtualRobotEngine engine;
    private final AgentMqttClient mqttClient;
    private final CommandDispatcher commandDispatcher;
    private final HeartbeatScheduler heartbeatScheduler;
    private final TelemetryScheduler telemetryScheduler;

    public VirtualC40AgentRunner(VirtualRobotEngine engine, SakarMqttConfig config, String agentVersion) {
        this.engine = engine;

        Map<String, RobotCommandExecutor> executorsByCommandType = new HashMap<>();
        RobotCommandExecutor virtualExecutor = new VirtualRobotCommandExecutor(engine);
        executorsByCommandType.put("GO_TO_POINT", virtualExecutor);
        executorsByCommandType.put("RETURN_TO_DOCK", virtualExecutor);
        this.commandDispatcher = new CommandDispatcher(new CompositeRobotCommandExecutor(executorsByCommandType));

        this.mqttClient = new AgentMqttClient(config, commandDispatcher);
        this.commandDispatcher.attachResultPublisher(mqttClient::publishEvent);

        this.heartbeatScheduler = new HeartbeatScheduler(mqttClient, agentVersion);
        this.telemetryScheduler = new TelemetryScheduler(mqttClient, new VirtualTelemetrySnapshotProvider(engine));
    }

    /** No-op if {@code config.isConfigured()} was false - same fallback every real agent already has. */
    public void start(int heartbeatIntervalSeconds, int telemetryIntervalSeconds) {
        mqttClient.connect();
        heartbeatScheduler.start(heartbeatIntervalSeconds);
        telemetryScheduler.start(telemetryIntervalSeconds);
    }

    public void stop() {
        heartbeatScheduler.shutdown();
        telemetryScheduler.shutdown();
        mqttClient.disconnect();
    }

    public VirtualRobotEngine getEngine() {
        return engine;
    }
}
