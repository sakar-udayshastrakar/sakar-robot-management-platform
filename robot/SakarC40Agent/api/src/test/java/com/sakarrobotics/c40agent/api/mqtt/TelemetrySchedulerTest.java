package com.sakarrobotics.c40agent.api.mqtt;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TelemetrySchedulerTest {

    @Mock
    private AgentMqttClient client;
    @Mock
    private TelemetrySnapshotProvider snapshotProvider;

    private TelemetryScheduler scheduler;

    @AfterEach
    void tearDown() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    @Test
    void start_pullsFromTheProviderAndPublishesImmediately() {
        when(snapshotProvider.currentReadings()).thenReturn(Collections.emptyList());
        scheduler = new TelemetryScheduler(client, snapshotProvider);

        scheduler.start(30);

        verify(client, timeout(1000)).publishTelemetry(anyList());
    }
}
