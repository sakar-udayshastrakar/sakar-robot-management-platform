package com.sakarrobotics.cloud.telemetry;

/**
 * The effective, Sakar-wide connectivity of a robot, derived from real
 * robot communication (heartbeat/telemetry) and the single configured
 * offline threshold — never from whether a vendor API call happened to
 * succeed.
 *
 * <p>{@link #UNKNOWN} is deliberately distinct from {@link #OFFLINE}: a
 * robot that has never reported at all has no evidence either way, and
 * {@code RobotOfflineWatcherService} never raises an OFFLINE alert for it
 * (its sweep only considers rows that are currently online and have gone
 * stale). Collapsing the two here would make the UI claim an offline
 * condition that the alert layer never asserted.
 */
public enum RobotConnectionStatus {

    /** Heartbeat/telemetry received within the configured offline threshold. */
    ONLINE,

    /**
     * Either the last heartbeat/telemetry is older than the configured
     * offline threshold, or an explicit offline signal was recorded (MQTT
     * Last Will, or Keenon reporting no data for the robot).
     */
    OFFLINE,

    /** No heartbeat or telemetry has ever been received for this robot. */
    UNKNOWN
}
