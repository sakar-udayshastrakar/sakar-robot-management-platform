package com.sakarrobotics.c40agent.api.mqtt;

/**
 * App-level MQTT connection configuration - the {@code :api} module's
 * counterpart to {@code SdkConnectionConfig} in {@code :sdk}. Never
 * constructed with hardcoded credentials - see
 * {@code SakarMqttConfig.fromBuildConfig()}-style factory in the app
 * module, which reads generated {@code BuildConfig} fields sourced from
 * the git-ignored {@code secrets.properties} at the project root.
 */
public final class SakarMqttConfig {

    private final String brokerUrl;
    private final String topicPrefix;
    private final AgentIdentity identity;
    private final String mqttUsername;
    private final String mqttPassword;
    private final int keepAliveSeconds;
    private final int qos;
    private final int heartbeatIntervalSeconds;
    private final int telemetryIntervalSeconds;
    private final int maxReconnectDelayMillis;
    private final int offlineQueueCapacity;
    private final String tlsTrustStorePath;
    private final String tlsTrustStorePassword;

    public SakarMqttConfig(String brokerUrl, String topicPrefix, AgentIdentity identity, String mqttUsername, String mqttPassword,
                            int keepAliveSeconds, int qos, int heartbeatIntervalSeconds, int telemetryIntervalSeconds,
                            int maxReconnectDelayMillis, int offlineQueueCapacity) {
        this(brokerUrl, topicPrefix, identity, mqttUsername, mqttPassword, keepAliveSeconds, qos,
                heartbeatIntervalSeconds, telemetryIntervalSeconds, maxReconnectDelayMillis, offlineQueueCapacity, null, null);
    }

    /**
     * @param tlsTrustStorePath Optional path to a BKS/JKS truststore for a private/self-signed
     * broker CA (Phase 3 Security Hardening — TLS), only meaningful when {@code brokerUrl} uses the
     * {@code ssl://} scheme. Leave {@code null}/blank to use the Android platform's default trust
     * store (real system CA validation, never trust-all) — the secure default Paho already applies
     * to any {@code ssl://} URL with zero extra configuration.
     */
    public SakarMqttConfig(String brokerUrl, String topicPrefix, AgentIdentity identity, String mqttUsername, String mqttPassword,
                            int keepAliveSeconds, int qos, int heartbeatIntervalSeconds, int telemetryIntervalSeconds,
                            int maxReconnectDelayMillis, int offlineQueueCapacity, String tlsTrustStorePath, String tlsTrustStorePassword) {
        this.brokerUrl = brokerUrl;
        this.topicPrefix = topicPrefix;
        this.identity = identity;
        this.mqttUsername = mqttUsername;
        this.mqttPassword = mqttPassword;
        this.keepAliveSeconds = keepAliveSeconds;
        this.qos = qos;
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
        this.telemetryIntervalSeconds = telemetryIntervalSeconds;
        this.maxReconnectDelayMillis = maxReconnectDelayMillis;
        this.offlineQueueCapacity = offlineQueueCapacity;
        this.tlsTrustStorePath = tlsTrustStorePath;
        this.tlsTrustStorePassword = tlsTrustStorePassword;
    }

    public String getTlsTrustStorePath() {
        return tlsTrustStorePath;
    }

    public String getTlsTrustStorePassword() {
        return tlsTrustStorePassword;
    }

    public String getTopicPrefix() {
        return topicPrefix;
    }

    public String getBrokerUrl() {
        return brokerUrl;
    }

    public AgentIdentity getIdentity() {
        return identity;
    }

    public String getMqttUsername() {
        return mqttUsername;
    }

    public String getMqttPassword() {
        return mqttPassword;
    }

    public int getKeepAliveSeconds() {
        return keepAliveSeconds;
    }

    public int getQos() {
        return qos;
    }

    public int getHeartbeatIntervalSeconds() {
        return heartbeatIntervalSeconds;
    }

    public int getTelemetryIntervalSeconds() {
        return telemetryIntervalSeconds;
    }

    public int getMaxReconnectDelayMillis() {
        return maxReconnectDelayMillis;
    }

    public int getOfflineQueueCapacity() {
        return offlineQueueCapacity;
    }

    public boolean isConfigured() {
        return brokerUrl != null && !brokerUrl.trim().isEmpty() && identity != null && identity.isComplete();
    }
}
