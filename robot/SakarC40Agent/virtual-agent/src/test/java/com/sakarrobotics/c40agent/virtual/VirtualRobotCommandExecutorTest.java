package com.sakarrobotics.c40agent.virtual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sakarrobotics.c40agent.api.mqtt.CommandDispatcher;
import com.sakarrobotics.c40agent.api.mqtt.RobotCommandResultReporter;
import com.sakarrobotics.c40agent.api.mqtt.dto.CommandPayload;

/**
 * Software-test-only coverage of {@link VirtualRobotCommandExecutor}
 * (Roadmap Phase 9, see ../../VIRTUAL_C40_SIMULATOR.md). No Android
 * runtime, no Peanut SDK, no MQTT broker, no physical robot anywhere in
 * this test. The last two tests drive the REAL {@link CommandDispatcher}
 * from :api (not reimplemented here) to prove duplicate-command and
 * expired-command handling already works for a virtual robot, using
 * exactly the same dispatcher the real robot's executors share.
 */
@ExtendWith(MockitoExtension.class)
class VirtualRobotCommandExecutorTest {

    private static final Gson GSON = new Gson();

    @Mock
    private RobotCommandResultReporter reporter;

    @Test
    void execute_goToPoint_routesToTheEngine() {
        VirtualRobotEngine engine = new VirtualRobotEngine("R1", "R1");
        VirtualRobotCommandExecutor executor = new VirtualRobotCommandExecutor(engine);

        executor.execute("GO_TO_POINT", Map.of("destinationId", VirtualMap.DESTINATION_ID_ROOM_A), reporter);

        verify(reporter).reportExecuting();
        verify(reporter, timeout(5000)).reportCompleted(contains("SIMULATED"));
    }

    @Test
    void execute_returnToDock_routesToTheEngine() {
        VirtualRobotEngine engine = new VirtualRobotEngine("R1", "R1");
        VirtualRobotCommandExecutor executor = new VirtualRobotCommandExecutor(engine);

        executor.execute("RETURN_TO_DOCK", Map.of(), reporter);

        verify(reporter).reportExecuting();
        verify(reporter, timeout(5000)).reportCompleted(contains("charging"));
    }

    @Test
    void execute_goToPoint_missingDestinationId_reportsFailedWithoutTouchingTheEngine() {
        VirtualRobotEngine engine = new VirtualRobotEngine("R1", "R1");
        VirtualRobotCommandExecutor executor = new VirtualRobotCommandExecutor(engine);

        executor.execute("GO_TO_POINT", Map.of(), reporter);

        verify(reporter).reportFailed(contains("destinationId"));
        verify(reporter, never()).reportExecuting();
        assertEquals(NavigationState.IDLE, engine.getState().getNavigationState());
    }

    @Test
    void execute_unsupportedCommandType_reportsFailedWithoutFabricatingSuccess() {
        VirtualRobotEngine engine = new VirtualRobotEngine("R1", "R1");
        VirtualRobotCommandExecutor executor = new VirtualRobotCommandExecutor(engine);

        executor.execute("START_TASK", Map.of(), reporter);

        verify(reporter).reportFailed(contains("not supported by the virtual simulator"));
        verify(reporter, never()).reportExecuting();
        verify(reporter, never()).reportCompleted(any());
    }

    @Test
    void fullDispatcherFlow_duplicateCommandId_isIgnoredAndNeverExecutedTwice() {
        // Reuses CommandDispatcher's own, already-tested dedup guard (CommandDispatcherTest in
        // :api) - this test proves it works unmodified with a virtual executor, not that dedup
        // itself needed reimplementing here.
        VirtualRobotEngine engine = new VirtualRobotEngine("R1", "R1");
        CommandDispatcher dispatcher = new CommandDispatcher(new VirtualRobotCommandExecutor(engine));
        List<String> publishedStatuses = new ArrayList<>();
        dispatcher.attachResultPublisher((eventType, severity, rawPayload) ->
                publishedStatuses.add(GSON.fromJson(rawPayload, JsonObject.class).get("status").getAsString()));

        CommandPayload command = commandPayload("dup-cmd-1", "GO_TO_POINT", futureExpiry(),
                Map.of("destinationId", (double) VirtualMap.DESTINATION_ID_ROOM_A));
        dispatcher.onCommandReceived(command);
        dispatcher.onCommandReceived(command); // exact same commandId, redelivered

        assertEquals(1, publishedStatuses.stream().filter("RECEIVED"::equals).count());
    }

    @Test
    void fullDispatcherFlow_expiredCommand_reportsTimeoutAndNeverReachesTheEngine() {
        VirtualRobotEngine engine = new VirtualRobotEngine("R1", "R1");
        CommandDispatcher dispatcher = new CommandDispatcher(new VirtualRobotCommandExecutor(engine));
        List<String> publishedStatuses = new ArrayList<>();
        dispatcher.attachResultPublisher((eventType, severity, rawPayload) ->
                publishedStatuses.add(GSON.fromJson(rawPayload, JsonObject.class).get("status").getAsString()));

        CommandPayload command = commandPayload("expired-cmd-1", "GO_TO_POINT",
                Instant.now().minusSeconds(60).toString(), Map.of("destinationId", (double) VirtualMap.DESTINATION_ID_ROOM_A));
        dispatcher.onCommandReceived(command);

        assertEquals(List.of("RECEIVED", "TIMEOUT"), publishedStatuses);
        assertEquals(NavigationState.IDLE, engine.getState().getNavigationState());
    }

    private static CommandPayload commandPayload(String commandId, String commandType, String expiresAt, Map<String, Object> params) {
        return new CommandPayload("1.0", commandId, "virtual-robot-1", Instant.now().toString(),
                "COMMAND", commandType, "nonce-1", expiresAt, params);
    }

    private static String futureExpiry() {
        return Instant.now().plusSeconds(30).toString();
    }
}
