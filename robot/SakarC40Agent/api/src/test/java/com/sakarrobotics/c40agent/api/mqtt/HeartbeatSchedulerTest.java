package com.sakarrobotics.c40agent.api.mqtt;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HeartbeatSchedulerTest {

    @Mock
    private AgentMqttClient client;

    private HeartbeatScheduler scheduler;

    @AfterEach
    void tearDown() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    @Test
    void start_publishesAHeartbeatImmediately() {
        scheduler = new HeartbeatScheduler(client, "0.1.0");

        scheduler.start(30); // interval is irrelevant here - the first tick fires with no initial delay

        verify(client, timeout(1000)).publishHeartbeat(eq("0.1.0"), anyLong(), eq("CONNECTED"));
    }

    @Test
    void stop_thenStart_stillPublishesAfterRestarting() {
        scheduler = new HeartbeatScheduler(client, "0.1.0");

        scheduler.start(30);
        verify(client, timeout(1000)).publishHeartbeat(any(), anyLong(), any());

        scheduler.stop();
        org.mockito.Mockito.clearInvocations(client);
        scheduler.start(30);

        verify(client, timeout(1000)).publishHeartbeat(any(), anyLong(), any());
    }
}
