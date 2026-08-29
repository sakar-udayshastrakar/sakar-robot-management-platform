package com.sakarrobotics.c40agent.api.mqtt;

import java.util.List;

import com.sakarrobotics.c40agent.api.mqtt.dto.TelemetryFieldReading;

/**
 * Supplies the current set of telemetry readings to publish (Phase 3 Part
 * 8/6). Implemented outside this module (by the {@code app} module,
 * wrapping {@code C40RobotController}) so this module never depends on
 * {@code :robot}/{@code :sdk} directly - the "MQTT Client -&gt; Agent
 * Communication Layer -&gt; Robot Controller / Telemetry Provider -&gt;
 * PeanutSdkBridge" layering is preserved by the composition root
 * ({@code SakarC40Application}) wiring this interface to a concrete
 * adapter, not by this module reaching down into the SDK chokepoint
 * itself.
 *
 * <p>Implementations must never fabricate a reading for a value that
 * isn't actually available - return fewer readings, not an invented one.
 */
public interface TelemetrySnapshotProvider {

    List<TelemetryFieldReading> currentReadings();
}
