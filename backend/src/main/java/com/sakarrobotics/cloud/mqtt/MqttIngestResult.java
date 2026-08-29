package com.sakarrobotics.cloud.mqtt;

/**
 * Outcome of {@link MqttInboundMessageService#handle}. Deliberately a
 * plain result type rather than a thrown exception — every rejection
 * listed here is an expected, routine outcome (a malformed message, an
 * unknown robot, a replayed duplicate), not a server bug, and is reported
 * back to the agent as an {@link com.sakarrobotics.cloud.mqtt.dto.AckPayload},
 * never as a 5xx-style failure.
 */
public record MqttIngestResult(boolean accepted, MqttRejectionReason reason, String detail) {

    public static MqttIngestResult ok() {
        return new MqttIngestResult(true, null, null);
    }

    public static MqttIngestResult rejected(MqttRejectionReason reason, String detail) {
        return new MqttIngestResult(false, reason, detail);
    }

    public String reasonCode() {
        return accepted ? null : reason.name();
    }
}
