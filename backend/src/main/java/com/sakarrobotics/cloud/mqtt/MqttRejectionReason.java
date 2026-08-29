package com.sakarrobotics.cloud.mqtt;

/** Stable, generic rejection vocabulary for MQTT ingestion (Phase 3 Part 14). */
public enum MqttRejectionReason {
    MALFORMED_MESSAGE,
    UNSUPPORTED_SCHEMA_VERSION,
    STALE_TIMESTAMP,
    UNKNOWN_ROBOT,
    UNAUTHORIZED_ROBOT,
    TENANT_MISMATCH,
    DUPLICATE_MESSAGE,
    PROCESSING_FAILED,
    /** Phase 3 hardening — envelope+payload exceeded {@code sakar.mqtt.max-payload-size-bytes}. */
    PAYLOAD_TOO_LARGE,
    /** Phase 3 hardening — {@code sequence} was negative (basic sanity, not strict monotonic ordering — see MqttInboundMessageService's Javadoc on why). */
    INVALID_SEQUENCE,
    /** Phase 3 hardening — this robot exceeded {@code sakar.mqtt.rate-limit-max-messages} within the configured window. */
    RATE_LIMITED
}
