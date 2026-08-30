package com.sakarrobotics.c40agent.api.mqtt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import com.sakarrobotics.c40agent.api.mqtt.dto.CommandPayload;

/**
 * Software-test-only coverage of {@link CommandDispatcher} (Roadmap Phase
 * 6/7 "Robot Agent Command Loop"). No MQTT broker, no Peanut SDK, no
 * physical robot involved anywhere in this test - it only exercises the
 * dispatch/idempotency/expiry/result-publishing logic.
 */
@ExtendWith(MockitoExtension.class)
class CommandDispatcherTest {

    private static final Gson GSON = new Gson();

    @Mock
    private RobotCommandExecutor executor;

    @Test
    void receivedCommand_reportsReceivedThenDelegatesToTheExecutor() {
        List<Published> published = new ArrayList<>();
        CommandDispatcher dispatcher = new CommandDispatcher(executor);
        dispatcher.attachResultPublisher(recordingPublisher(published));

        dispatcher.onCommandReceived(command("cmd-1", "START_TASK", futureExpiry()));

        assertEquals(1, published.size());
        assertEquals("RECEIVED", statusOf(published.get(0)));
        verify(executor, timeout(1000)).execute(org.mockito.ArgumentMatchers.eq("START_TASK"), any(), any());
    }

    @Test
    void executorReportingCompleted_publishesACompletedResultWithDuration() {
        List<Published> published = new ArrayList<>();
        CommandDispatcher dispatcher = new CommandDispatcher((commandType, params, reporter) -> reporter.reportCompleted("all good"));
        dispatcher.attachResultPublisher(recordingPublisher(published));

        dispatcher.onCommandReceived(command("cmd-2", "START_TASK", futureExpiry()));

        assertEquals(2, published.size()); // RECEIVED, then COMPLETED
        assertEquals("RECEIVED", statusOf(published.get(0)));
        assertEquals("COMPLETED", statusOf(published.get(1)));
        JsonObject detail = GSON.fromJson(published.get(1).rawPayload, JsonObject.class);
        assertEquals("all good", detail.get("detail").getAsString());
        assertTrue(detail.has("durationMs"));
    }

    @Test
    void executorReportingFailed_publishesAFailedResultWithErrorSeverity() {
        List<Published> published = new ArrayList<>();
        CommandDispatcher dispatcher = new CommandDispatcher((commandType, params, reporter) -> reporter.reportFailed("boom"));
        dispatcher.attachResultPublisher(recordingPublisher(published));

        dispatcher.onCommandReceived(command("cmd-3", "START_TASK", futureExpiry()));

        Published failedEvent = published.get(1);
        assertEquals("FAILED", statusOf(failedEvent));
        assertEquals("ERROR", failedEvent.severity);
    }

    @Test
    void executorReportingDispatched_publishesADispatchedResult_notCompleted() {
        List<Published> published = new ArrayList<>();
        CommandDispatcher dispatcher = new CommandDispatcher(
                (commandType, params, reporter) -> reporter.reportDispatched("accepted by local control interface"));
        dispatcher.attachResultPublisher(recordingPublisher(published));

        dispatcher.onCommandReceived(command("cmd-return-to-dock", "RETURN_TO_DOCK", futureExpiry()));

        assertEquals(2, published.size()); // RECEIVED, then DISPATCHED
        assertEquals("RECEIVED", statusOf(published.get(0)));
        assertEquals("DISPATCHED", statusOf(published.get(1)));
        assertEquals("INFO", published.get(1).severity); // DISPATCHED is not an error/timeout
        JsonObject detail = GSON.fromJson(published.get(1).rawPayload, JsonObject.class);
        assertEquals("accepted by local control interface", detail.get("detail").getAsString());
    }

    @Test
    void fullReturnToDockFlow_realExecutorWithAFakeGateway_publishesReceivedThenDispatched() {
        // Exercises the real composition CommandDispatcher -> PeanutSdkReturnToDockExecutor,
        // with only the Peanut-SDK-touching boundary (ReturnToDockGateway) faked — no Android
        // runtime, no real SDK, no physical robot. SOFTWARE TEST VERIFIED, not physical.
        List<Published> published = new ArrayList<>();
        ReturnToDockGateway immediatelyAcceptingGateway = callback -> callback.onAccepted("{\"code\":0}");
        CommandDispatcher dispatcher = new CommandDispatcher(new PeanutSdkReturnToDockExecutor(immediatelyAcceptingGateway));
        dispatcher.attachResultPublisher(recordingPublisher(published));

        dispatcher.onCommandReceived(command("cmd-real-return-to-dock", "RETURN_TO_DOCK", futureExpiry()));

        // RECEIVED (CommandDispatcher) -> EXECUTING (PeanutSdkReturnToDockExecutor.execute's own
        // first action) -> DISPATCHED (the fake gateway accepts synchronously).
        assertEquals(3, published.size());
        assertEquals("RECEIVED", statusOf(published.get(0)));
        assertEquals("EXECUTING", statusOf(published.get(1)));
        assertEquals("DISPATCHED", statusOf(published.get(2)));
    }

    @Test
    void duplicateCommandId_isIgnoredAndNeverExecutedTwice() {
        List<Published> published = new ArrayList<>();
        CommandDispatcher dispatcher = new CommandDispatcher(executor);
        dispatcher.attachResultPublisher(recordingPublisher(published));
        CommandPayload payload = command("cmd-4", "START_TASK", futureExpiry());

        dispatcher.onCommandReceived(payload);
        dispatcher.onCommandReceived(payload); // exact same commandId, redelivered

        // times(1), not never() — the point is the SECOND delivery adds nothing, not that the
        // first delivery didn't happen. A brief timeout only covers the async executor dispatch.
        verify(executor, timeout(1000).times(1)).execute(any(), any(), any());
        assertEquals(1, published.stream().filter(p -> "RECEIVED".equals(statusOf(p))).count());
    }

    @Test
    void expiredCommand_reportsTimeoutAndNeverCallsTheExecutor() {
        List<Published> published = new ArrayList<>();
        CommandDispatcher dispatcher = new CommandDispatcher(executor);
        dispatcher.attachResultPublisher(recordingPublisher(published));
        String pastExpiry = Instant.now().minusSeconds(60).toString();

        dispatcher.onCommandReceived(command("cmd-5", "START_TASK", pastExpiry));

        assertEquals(2, published.size());
        assertEquals("RECEIVED", statusOf(published.get(0)));
        assertEquals("TIMEOUT", statusOf(published.get(1)));
        verify(executor, never()).execute(any(), any(), any());
    }

    @Test
    void noPublisherAttached_neverThrows() {
        CommandDispatcher dispatcher = new CommandDispatcher(executor);
        // attachResultPublisher deliberately never called.

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> dispatcher.onCommandReceived(command("cmd-6", "START_TASK", futureExpiry())));
    }

    // ---------------------------------------------------------------

    private static CommandPayload command(String commandId, String commandType, String expiresAt) {
        return new CommandPayload("1.0", commandId, "robot-1", Instant.now().toString(),
                "COMMAND", commandType, "nonce-1", expiresAt, Map.of());
    }

    private static String futureExpiry() {
        return Instant.now().plusSeconds(30).toString();
    }

    private static CommandDispatcher.ResultPublisher recordingPublisher(List<Published> sink) {
        return (eventType, severity, rawPayload) -> sink.add(new Published(eventType, severity, rawPayload));
    }

    private static String statusOf(Published published) {
        return GSON.fromJson(published.rawPayload, JsonObject.class).get("status").getAsString();
    }

    private static final class Published {
        private final String eventType;
        private final String severity;
        private final String rawPayload;

        private Published(String eventType, String severity, String rawPayload) {
            this.eventType = eventType;
            this.severity = severity;
            this.rawPayload = rawPayload;
        }
    }
}
