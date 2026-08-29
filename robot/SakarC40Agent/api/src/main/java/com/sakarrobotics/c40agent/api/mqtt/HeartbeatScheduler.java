package com.sakarrobotics.c40agent.api.mqtt;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Periodic agent heartbeat (Phase 3 Part 7) - resumes automatically after a reconnect, since it just keeps calling {@link AgentMqttClient#publishHeartbeat}. */
public final class HeartbeatScheduler {

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sakar-mqtt-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    private final AgentMqttClient client;
    private final String agentVersion;
    private final long startedAtMillis = System.currentTimeMillis();
    private volatile ScheduledFuture<?> future;

    public HeartbeatScheduler(AgentMqttClient client, String agentVersion) {
        this.client = client;
        this.agentVersion = agentVersion;
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
        long uptimeSeconds = (System.currentTimeMillis() - startedAtMillis) / 1000;
        client.publishHeartbeat(agentVersion, uptimeSeconds, "CONNECTED");
    }
}
