package com.sakarrobotics.cloud.mqtt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Robot-facing MQTT transport config (Master Requirements Part 15/23:
 * telemetry, events, command delivery, heartbeats, LWT-based offline
 * detection). {@code enabled=false} by default — no broker connection is
 * ever attempted unless a deployment explicitly turns this on, so the
 * backend starts cleanly with no broker present (dev/test).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "sakar.mqtt")
public class MqttProperties {

    private boolean enabled = false;

    private String brokerUrl = "tcp://localhost:1883";

    private String clientIdPrefix = "sakar-cloud-";

    private String username;

    private String password;

    private int qos = 1;

    /** First segment of every topic (Phase 3) — see {@link MqttTopicResolver}. */
    private String topicPrefix = "sakar";

    /** Envelope {@code schemaVersion} this backend currently accepts (Phase 3). */
    private String schemaVersion = "1.0";

    /**
     * Maximum age (in either direction) an envelope's {@code timestamp} may
     * differ from server time before it is rejected as {@code STALE_TIMESTAMP}
     * — bounds clock-skew tolerance without requiring NTP-perfect agents.
     * Because this checks {@code abs()} of the difference, a
     * timestamp-from-the-future beyond this window is already rejected the
     * same way a stale one is (Phase 3 Security Hardening Part 7 — "future
     * timestamp rejection" is this same check, not a separate one).
     */
    private int maxTimestampSkewSeconds = 300;

    /**
     * Hard cap on the raw MQTT payload byte size, enforced by {@link
     * MqttInboundListener} <em>before</em> any JSON parsing is attempted
     * (Phase 3 Security Hardening — DoS/memory-exhaustion protection).
     * 64 KiB comfortably exceeds a realistic batched-telemetry envelope
     * while remaining far below anything that could meaningfully stress
     * the JVM heap per message.
     */
    private int maxPayloadSizeBytes = 65_536;

    /**
     * Per-robot MQTT ingestion rate limit (Phase 3 Security Hardening —
     * flood/DoS protection), Redis-backed, same mechanism {@code
     * LoginRateLimiterService} already uses. A robot publishing faster than
     * this is rejected with {@code RATE_LIMITED} until the window rolls
     * over — deliberately generous (a genuine heartbeat/telemetry cadence
     * is on the order of once every 30-60s, not once a second) so it only
     * ever catches a flooding/misbehaving client, never normal traffic.
     */
    private int rateLimitMaxMessages = 120;

    private int rateLimitWindowSeconds = 60;

    /**
     * Optional path to a JKS truststore for a private/self-signed broker CA
     * (Phase 3 Security Hardening — TLS). Leave blank to use the JVM's
     * default trust store (i.e. real, publicly-trusted CA validation via
     * Eclipse Paho's own {@code ssl://} handling) — this is the secure
     * default, not an insecure one; nothing in this codebase ever disables
     * certificate or hostname verification. Only meaningful when {@link
     * #brokerUrl} uses the {@code ssl://} scheme.
     */
    private String tlsTrustStorePath;

    private String tlsTrustStorePassword;
}
