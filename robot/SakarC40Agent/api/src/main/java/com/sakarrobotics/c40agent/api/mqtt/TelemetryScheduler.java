package com.sakarrobotics.c40agent.api.mqtt;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Periodic telemetry publish (Phase 3 Part 8) - reads from {@link TelemetrySnapshotProvider}, never fabricates a reading itself. */
public final class TelemetryScheduler {

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sakar-mqtt-telemetry");
        thread.setDaemon(true);
        return thread;
    });

    private final AgentMqttClient client;
    private final TelemetrySnapshotProvider snapshotProvider;
    private volatile ScheduledFuture<?> future;

    public TelemetryScheduler(AgentMqttClient client, TelemetrySnapshotProvider snapshotProvider) {
        this.client = client;
        this.snapshotProvider = snapshotProvider;
    }

    public synchronized void start(int intervalSeconds) {
        stop();
        future = executor.scheduleAtFixedRate(this::publish, 0, Math.max(5, intervalSeconds), TimeUnit.SECONDS);
    }

    public synchronized void stop() {
        if (future != null) {
            future.cancel(false);
            future = null;
        }
    }

    public void shutdown() {
        stop();
        executor.shutdownNow();
    }

    private void publish() {
        client.publishTelemetry(snapshotProvider.currentReadings());
    }
}
