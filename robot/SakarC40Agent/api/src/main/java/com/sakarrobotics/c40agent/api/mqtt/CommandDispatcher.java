package com.sakarrobotics.c40agent.api.mqtt;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.sakarrobotics.c40agent.api.mqtt.dto.CommandPayload;
import com.sakarrobotics.c40agent.api.mqtt.dto.CommandResultDetail;
import com.sakarrobotics.c40agent.logging.SdkCallLogger;

/**
 * Receives inbound {@link CommandPayload}s (via {@link
 * AgentMqttListener#onCommandReceived}) and drives a {@link
 * RobotCommandExecutor}, reporting every lifecycle transition back to the
 * Sakar Cloud backend over the existing EVENT channel with {@code
 * eventType="COMMAND_RESULT"} (Roadmap Phase 6/7 "Robot Agent Command
 * Loop" — see the backend's {@code CommandResultIngestionService}, which
 * this class's wire format must stay in sync with).
 *
 * <p>Guards against two idempotency hazards a real MQTT deployment can hit:
 * <ul>
 *   <li>executing the same {@code commandId} twice (an MQTT redelivery, or
 *   a reconnect racing an in-flight execution) — a small bounded
 *   "already seen" set makes a repeat delivery a safe no-op;</li>
 *   <li>executing a command whose {@code expiresAt} has already passed by
 *   the time it's received — reported as {@code TIMEOUT} immediately,
 *   without ever calling the executor.</li>
 * </ul>
 *
 * <p>This class never imports {@code com.keenon.*} and never depends on
 * {@code :sdk}/{@code :robot} — see {@link RobotCommandExecutor}'s Javadoc
 * for how that boundary is preserved.
 */
public final class CommandDispatcher implements AgentMqttListener {

    private static final int MAX_TRACKED_COMMAND_IDS = 200;

    private final RobotCommandExecutor executor;
    private final Gson gson = new Gson();

    /** Bounded LRU set of recently-seen command ids, guarding against double execution. */
    private final Map<String, Boolean> seenCommandIds = Collections.synchronizedMap(
            new LinkedHashMap<String, Boolean>(16, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_TRACKED_COMMAND_IDS;
                }
            });

    private volatile ResultPublisher resultPublisher;

    public CommandDispatcher(RobotCommandExecutor executor) {
        this.executor = executor;
    }

    /**
     * Deliberately not a constructor argument: {@link AgentMqttClient}
     * needs this dispatcher as its listener at construction time, but this
     * dispatcher needs the client (to publish results) only after that —
     * call this once, right after constructing the {@code AgentMqttClient}
     * this dispatcher was registered on, e.g. {@code
     * dispatcher.attachResultPublisher(mqttClient::publishEvent)}.
     */
    public void attachResultPublisher(ResultPublisher resultPublisher) {
        this.resultPublisher = resultPublisher;
    }

    @Override
    public void onCommandReceived(CommandPayload command) {
        String commandId = command.getCommandId();
        if (commandId == null || commandId.isEmpty()) {
            SdkCallLogger.getInstance().logError("CommandDispatcher.onCommandReceived", "n/a", -1,
                    "command missing messageId/commandId - ignored");
            return;
        }
        if (seenCommandIds.putIfAbsent(commandId, Boolean.TRUE) != null) {
            SdkCallLogger.getInstance().logError("CommandDispatcher.onCommandReceived", commandId, -1,
                    "duplicate commandId - already processed, ignored (idempotency guard)");
            return;
        }

        reportResult(commandId, "RECEIVED", null, null);

        if (isExpired(command)) {
            reportResult(commandId, "TIMEOUT", "Command already expired (expiresAt=" + command.getExpiresAt() + ") by the time it was received", null);
            return;
        }

        long startedAtMillis = System.currentTimeMillis();
        executor.execute(command.getCommandType(), command.getParams(), new RobotCommandResultReporter() {
            @Override
            public void reportExecuting() {
                reportResult(commandId, "EXECUTING", null, null);
            }

            @Override
            public void reportCompleted(String detail) {
                reportResult(commandId, "COMPLETED", detail, System.currentTimeMillis() - startedAtMillis);
            }

            @Override
            public void reportDispatched(String detail) {
                reportResult(commandId, "DISPATCHED", detail, System.currentTimeMillis() - startedAtMillis);
            }

            @Override
            public void reportFailed(String detail) {
                reportResult(commandId, "FAILED", detail, System.currentTimeMillis() - startedAtMillis);
            }
        });
    }

    private boolean isExpired(CommandPayload command) {
        if (command.getExpiresAt() == null || command.getExpiresAt().isEmpty()) {
            return false;
        }
        try {
            return Instant.parse(command.getExpiresAt()).isBefore(Instant.now());
        } catch (RuntimeException malformed) {
            return false; // an unparsable expiry must not block execution
        }
    }

    private void reportResult(String commandId, String status, String detail, Long durationMs) {
        ResultPublisher publisher = resultPublisher;
        if (publisher == null) {
            SdkCallLogger.getInstance().logError("CommandDispatcher.reportResult", commandId, -1,
                    "no result publisher attached yet - " + status + " result was dropped");
            return;
        }
        CommandResultDetail resultDetail = new CommandResultDetail(commandId, status, detail, durationMs);
        String severity = "FAILED".equals(status) || "TIMEOUT".equals(status) ? "ERROR" : "INFO";
        publisher.publish("COMMAND_RESULT", severity, gson.toJson(resultDetail));
    }

    /** Narrow seam so this class never needs the full {@link AgentMqttClient} type. */
    public interface ResultPublisher {
        void publish(String eventType, String severity, String rawPayload);
    }
}
