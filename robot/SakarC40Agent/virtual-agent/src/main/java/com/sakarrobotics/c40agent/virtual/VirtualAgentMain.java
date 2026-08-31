package com.sakarrobotics.c40agent.virtual;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.sakarrobotics.c40agent.api.mqtt.AgentIdentity;
import com.sakarrobotics.c40agent.api.mqtt.CommandDispatcher;
import com.sakarrobotics.c40agent.api.mqtt.CompositeRobotCommandExecutor;
import com.sakarrobotics.c40agent.api.mqtt.RobotCommandExecutor;
import com.sakarrobotics.c40agent.api.mqtt.SakarMqttConfig;
import com.sakarrobotics.c40agent.api.mqtt.dto.CommandPayload;
import com.sakarrobotics.c40agent.logging.SdkCallLogger;
import com.sakarrobotics.c40agent.telemetry.Destination;

/**
 * DEV/VALIDATION-ONLY runtime driver for the Virtual C40 Simulator
 * (Roadmap Phase 9, see ../../VIRTUAL_C40_SIMULATOR.md). NOT part of any
 * production deployment path — this class exists solely to run the
 * existing {@link VirtualRobotRegistry}/{@link VirtualC40AgentRunner}/
 * {@link CommandDispatcher}/{@link VirtualRobotCommandExecutor} as a real
 * OS process for an end-to-end runtime validation pass, since none of
 * those classes previously had any standalone entry point.
 *
 * <p>Every command in the scripted validation below is fed into a real
 * {@link CommandDispatcher#onCommandReceived} — the exact same method a
 * real inbound MQTT message reaches via {@code AgentMqttClient.handleCommand()}
 * after deserializing the identical {@link CommandPayload} JSON shape off
 * the wire. No broker is available in this environment (no Docker,
 * Postgres, Redis, or Mosquitto installed), so this harness does not
 * pretend a message crossed a real socket for that hop — it calls the
 * same downstream method a socket read would have called. Each
 * {@link VirtualC40AgentRunner#start} call below DOES make a real
 * Paho MQTT connection attempt to a real TCP address; expect and treat as
 * correct a logged connection failure, proving the real client code path
 * runs without a broker to fake success against.
 *
 * <p>This class never imports {@code com.keenon.*}, never references a
 * physical robot's network address, and this module's own dependency
 * graph makes it impossible for it to reach {@code PeanutSdkBridge} or
 * {@code C40RobotController} even by mistake — see
 * {@code VirtualAgentRealSdkIsolationTest}.
 */
public final class VirtualAgentMain {

    private VirtualAgentMain() {
    }

    public static void main(String[] args) throws InterruptedException {
        String brokerUrl = System.getenv().getOrDefault("SAKAR_MQTT_BROKER_URL", "tcp://localhost:1883");
        String topicPrefix = System.getenv().getOrDefault("SAKAR_MQTT_TOPIC_PREFIX", "sakar");
        String orgId = System.getenv().getOrDefault("SAKAR_ORG_ID", "virtual-validation-org");

        log("MAIN", "=== Virtual C40 Agent runtime validation harness starting ===");
        log("MAIN", "SIMULATED ROBOTS ONLY. No physical C40 IP, no CoAP, no Keenon Cloud transport anywhere in this class.");
        log("MAIN", "MQTT broker target: " + brokerUrl + " (expected UNREACHABLE in this environment - no broker installed here)");

        VirtualRobotRegistry registry = VirtualRobotRegistry.withDefaultFleet();
        Map<String, VirtualC40AgentRunner> runners = new LinkedHashMap<>();
        Map<String, CommandDispatcher> dispatchers = new LinkedHashMap<>();

        for (Map.Entry<String, VirtualRobotEngine> entry : registry.all().entrySet()) {
            String robotId = entry.getKey();
            VirtualRobotEngine engine = entry.getValue();

            AgentIdentity identity = new AgentIdentity(orgId, null, robotId, robotId + "-agent");
            SakarMqttConfig config = new SakarMqttConfig(brokerUrl, topicPrefix, identity, "", "",
                    60, 1, 30, 30, 30000, 100);
            VirtualC40AgentRunner runner = new VirtualC40AgentRunner(engine, config, "virtual-agent-runtime-validation-1.0");
            runner.start(30, 30);
            runners.put(robotId, runner);

            RobotCommandExecutor executor = new VirtualRobotCommandExecutor(engine);
            Map<String, RobotCommandExecutor> executorsByCommandType = new HashMap<>();
            executorsByCommandType.put("GO_TO_POINT", executor);
            executorsByCommandType.put("RETURN_TO_DOCK", executor);
            CommandDispatcher dispatcher = new CommandDispatcher(new CompositeRobotCommandExecutor(executorsByCommandType));
            dispatcher.attachResultPublisher((eventType, severity, rawPayload) ->
                    log("BACKEND-RESULT[" + robotId + "]", eventType + "/" + severity + " " + rawPayload));
            dispatchers.put(robotId, dispatcher);
        }

        log("MAIN", "3 VirtualC40AgentRunner processes wired: " + registry.all().keySet());
        sleep(500);

        // ---- Phase 4: verify all 3 virtual robots ----
        log("PHASE4", "Initial fleet state:");
        for (VirtualRobotEngine engine : registry.all().values()) {
            printState("PHASE4", engine);
        }

        // ---- Phase 5: destination discovery ----
        String robot1 = VirtualRobotRegistry.DEFAULT_ROBOT_1;
        String robot2 = VirtualRobotRegistry.DEFAULT_ROBOT_2;
        String robot3 = VirtualRobotRegistry.DEFAULT_ROBOT_3;
        List<Destination> destinations = registry.get(robot1).getAllDestinations();
        log("PHASE5", "Destinations discovered via " + robot1 + " (map=" + VirtualMap.MAP_ID + "):");
        for (Destination d : destinations) {
            log("PHASE5", "  id=" + d.getId() + " name=" + d.getName() + " mapId=" + d.getMapId());
        }
        log("PHASE5", "These destination IDs (1001-1004) are simulation-only and must never be used on a physical C40.");

        // ---- Phase 6: GO_TO_POINT on VIRTUAL-C40-001, destination 1002 ----
        log("PHASE6", "Dispatching GO_TO_POINT destinationId=1002 to " + robot1);
        String cmd1 = dispatchGoToPoint(dispatchers.get(robot1), robot1, VirtualMap.DESTINATION_ID_RECEPTION);
        printState("PHASE6-immediately-after-dispatch", registry.get(robot1));
        sleep(600);
        log("PHASE6", "commandId=" + cmd1 + " should now be ARRIVED:");
        printState("PHASE6-after-600ms", registry.get(robot1));

        // ---- Phase 7: RETURN_TO_DOCK on VIRTUAL-C40-001 ----
        log("PHASE7", "Dispatching RETURN_TO_DOCK to " + robot1);
        dispatchReturnToDock(dispatchers.get(robot1), robot1);
        sleep(600);
        printState("PHASE7-after-600ms", registry.get(robot1));

        // ---- Phase 8: failure scenarios, all on VIRTUAL-C40-002 (the designated failure-test robot) ----
        VirtualRobotEngine engine2 = registry.get(robot2);

        log("PHASE8.1", "Invalid destination (999999) on " + robot2);
        dispatchGoToPoint(dispatchers.get(robot2), robot2, 999999);
        sleep(200);

        log("PHASE8.2", "Offline robot: setOnline(false) then GO_TO_POINT on " + robot2);
        engine2.setOnline(false);
        dispatchGoToPoint(dispatchers.get(robot2), robot2, VirtualMap.DESTINATION_ID_LOBBY);
        sleep(200);
        engine2.setOnline(true);
        log("PHASE8.2", "restored " + robot2 + " online=true");

        log("PHASE8.3", "Navigation blocked (one-shot) on " + robot2);
        engine2.setBlocked(true);
        dispatchGoToPoint(dispatchers.get(robot2), robot2, VirtualMap.DESTINATION_ID_LOBBY);
        sleep(200);
        printState("PHASE8.3-after-blocked-attempt", engine2);
        log("PHASE8.3", "Retrying same destination - one-shot block should have cleared:");
        dispatchGoToPoint(dispatchers.get(robot2), robot2, VirtualMap.DESTINATION_ID_LOBBY);
        sleep(600);
        printState("PHASE8.3-after-retry", engine2);

        log("PHASE8.4", "Draining " + robot2 + "'s battery via repeated real navigations to force insufficient-battery failure");
        int beforeBattery = engine2.getState().getBatteryPercentage();
        int[] alternatingDestinations = {VirtualMap.DESTINATION_ID_ROOM_A, VirtualMap.DESTINATION_ID_LOBBY};
        int tripCount = 0;
        while (engine2.getState().getBatteryPercentage() >= VirtualRobotEngine.DEFAULT_MINIMUM_BATTERY_TO_NAVIGATE && tripCount < 25) {
            dispatchGoToPoint(dispatchers.get(robot2), robot2, alternatingDestinations[tripCount % 2]);
            sleep(400);
            tripCount++;
        }
        log("PHASE8.4", "Drained from " + beforeBattery + "% to " + engine2.getState().getBatteryPercentage()
                + "% over " + tripCount + " real navigations");
        log("PHASE8.4", "Now attempting one more GO_TO_POINT - expect a deterministic insufficient-battery failure:");
        dispatchGoToPoint(dispatchers.get(robot2), robot2, VirtualMap.DESTINATION_ID_ROOM_A);
        sleep(200);
        printState("PHASE8.4-after-insufficient-battery-attempt", engine2);

        log("PHASE8.5", "Expired command on " + robot2);
        CommandPayload expired = buildCommand(robot2, "GO_TO_POINT",
                mapOf("destinationId", VirtualMap.DESTINATION_ID_LOBBY), Instant.now().minusSeconds(60).toString());
        dispatchers.get(robot2).onCommandReceived(expired);
        sleep(200);

        log("PHASE8.6", "Duplicate command id on " + robot2);
        CommandPayload duplicate = buildCommand(robot2, "RETURN_TO_DOCK", null, null);
        log("PHASE8.6", "First delivery of commandId=" + duplicate.getCommandId());
        dispatchers.get(robot2).onCommandReceived(duplicate);
        sleep(50);
        log("PHASE8.6", "Second (duplicate) delivery of the SAME commandId - expect it to be silently ignored:");
        dispatchers.get(robot2).onCommandReceived(duplicate);
        sleep(600);

        // ---- Phase 9: multi-robot isolation ----
        VirtualRobotEngine engine3 = registry.get(robot3);
        log("PHASE9", "Snapshotting " + robot2 + " and " + robot3 + " BEFORE commanding " + robot1);
        VirtualRobotState robot2Before = engine2.getState();
        VirtualRobotState robot3Before = engine3.getState();
        printState("PHASE9-before", engine2);
        printState("PHASE9-before", engine3);

        log("PHASE9", "Commanding ONLY " + robot1 + " (GO_TO_POINT destinationId=1001)");
        dispatchGoToPoint(dispatchers.get(robot1), robot1, VirtualMap.DESTINATION_ID_LOBBY);
        sleep(600);
        printState("PHASE9-robot1-after", registry.get(robot1));

        VirtualRobotState robot2After = engine2.getState();
        VirtualRobotState robot3After = engine3.getState();
        printState("PHASE9-after", engine2);
        printState("PHASE9-after", engine3);

        boolean robot2Unaffected = statesEqual(robot2Before, robot2After);
        boolean robot3Unaffected = statesEqual(robot3Before, robot3After);
        log("PHASE9", robot2 + " unaffected by " + robot1 + "'s command: " + robot2Unaffected);
        log("PHASE9", robot3 + " unaffected by " + robot1 + "'s command: " + robot3Unaffected);

        // ---- Phase 11: surface the real MQTT/Paho connect attempts (SdkCallLogger is an
        // in-memory ring buffer with no console output by design - dump it here so the
        // connection-failure evidence is visible in this harness's own log). ----
        log("PHASE11", "SdkCallLogger entries recorded during this run (proves the real Paho client code path executed):");
        for (com.sakarrobotics.c40agent.logging.LogEntry entry : SdkCallLogger.getInstance().getEntries()) {
            log("PHASE11", "  api=" + entry.getApi() + " request=" + entry.getRequest() + " success=" + entry.isSuccess()
                    + " errorCode=" + entry.getErrorCode() + " errorMessage=" + entry.getErrorMessage());
        }

        // ---- shutdown ----
        for (VirtualC40AgentRunner runner : runners.values()) {
            runner.stop();
        }
        log("MAIN", "=== Virtual C40 Agent runtime validation harness complete - all runners stopped ===");
        System.exit(0);
    }

    private static String dispatchGoToPoint(CommandDispatcher dispatcher, String robotId, int destinationId) {
        CommandPayload payload = buildCommand(robotId, "GO_TO_POINT", mapOf("destinationId", destinationId), null);
        dispatcher.onCommandReceived(payload);
        return payload.getCommandId();
    }

    private static String dispatchReturnToDock(CommandDispatcher dispatcher, String robotId) {
        CommandPayload payload = buildCommand(robotId, "RETURN_TO_DOCK", null, null);
        dispatcher.onCommandReceived(payload);
        return payload.getCommandId();
    }

    private static CommandPayload buildCommand(String robotId, String commandType, Map<String, Object> params, String expiresAt) {
        return new CommandPayload("1.0", UUID.randomUUID().toString(), robotId, Instant.now().toString(),
                "COMMAND", commandType, UUID.randomUUID().toString(), expiresAt, params);
    }

    private static Map<String, Object> mapOf(String key, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    private static boolean statesEqual(VirtualRobotState a, VirtualRobotState b) {
        return a.isOnline() == b.isOnline()
                && a.getBatteryPercentage() == b.getBatteryPercentage()
                && a.isCharging() == b.isCharging()
                && java.util.Objects.equals(a.getCurrentDestinationId(), b.getCurrentDestinationId())
                && a.getCurrentX() == b.getCurrentX()
                && a.getCurrentY() == b.getCurrentY()
                && a.getNavigationState() == b.getNavigationState()
                && java.util.Objects.equals(a.getLastCommandId(), b.getLastCommandId());
    }

    private static void printState(String label, VirtualRobotEngine engine) {
        printState(label, engine.getState());
    }

    private static void printState(String label, VirtualRobotState s) {
        log(label, String.format("robotId=%s online=%s battery=%d%% charging=%s map=%s destId=%s pos=(%.2f,%.2f,%.2f) nav=%s lastCommandId=%s",
                s.getRobotId(), s.isOnline(), s.getBatteryPercentage(), s.isCharging(), s.getCurrentMapId(),
                s.getCurrentDestinationId(), s.getCurrentX(), s.getCurrentY(), s.getCurrentZ(), s.getNavigationState(), s.getLastCommandId()));
    }

    private static void sleep(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    private static void log(String tag, String message) {
        System.out.println("[" + Instant.now() + "][" + tag + "] " + message);
        System.out.flush();
    }
}
