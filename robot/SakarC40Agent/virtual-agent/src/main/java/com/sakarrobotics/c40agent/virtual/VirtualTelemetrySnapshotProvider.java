package com.sakarrobotics.c40agent.virtual;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import com.sakarrobotics.c40agent.api.mqtt.TelemetrySnapshotProvider;
import com.sakarrobotics.c40agent.api.mqtt.dto.TelemetryFieldReading;

/**
 * {@link TelemetrySnapshotProvider} backed by a {@link VirtualRobotEngine}
 * (Roadmap Phase 9, see ../../VIRTUAL_C40_SIMULATOR.md) - the SAME
 * interface {@code C40TelemetrySnapshotProvider} implements for the real
 * robot, so {@code TelemetryScheduler}/{@code AgentMqttClient} need no
 * changes to publish a virtual robot's battery/position/navigation state
 * exactly like a real one's telemetry.
 *
 * <p>Every reading's {@code metric} name is prefixed {@code "simulated."}
 * so a consumer reading raw telemetry can never mistake this for a
 * physical-robot reading, in addition to whatever the robot's own
 * manufacturer/model registration already signals.
 */
public final class VirtualTelemetrySnapshotProvider implements TelemetrySnapshotProvider {

    private final VirtualRobotEngine engine;

    public VirtualTelemetrySnapshotProvider(VirtualRobotEngine engine) {
        this.engine = engine;
    }

    @Override
    public List<TelemetryFieldReading> currentReadings() {
        VirtualRobotState state = engine.getState();
        String now = Instant.now().toString();
        return Arrays.asList(
                new TelemetryFieldReading("simulated.battery_percentage", null, (double) state.getBatteryPercentage(), now),
                new TelemetryFieldReading("simulated.charging", String.valueOf(state.isCharging()), null, now),
                new TelemetryFieldReading("simulated.navigation_state", state.getNavigationState().name(), null, now),
                new TelemetryFieldReading("simulated.map_id", state.getCurrentMapId(), null, now),
                new TelemetryFieldReading("simulated.position_x", null, state.getCurrentX(), now),
                new TelemetryFieldReading("simulated.position_y", null, state.getCurrentY(), now),
                new TelemetryFieldReading("simulated.position_z", null, state.getCurrentZ(), now));
    }
}
