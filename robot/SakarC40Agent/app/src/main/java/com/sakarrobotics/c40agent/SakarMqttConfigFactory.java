package com.sakarrobotics.c40agent;

import com.sakarrobotics.c40agent.api.mqtt.AgentIdentity;
import com.sakarrobotics.c40agent.api.mqtt.SakarMqttConfig;

/**
 * Builds {@link SakarMqttConfig} from this module's {@code BuildConfig}
 * fields (Phase 3), which Gradle generates from the git-ignored
 * {@code secrets.properties} at the project root - the same pattern
 * {@code SdkConnectionConfig.fromBuildConfig()} already uses for the
 * Peanut SDK's own credentials. This is the only supported way to obtain
 * a config in app code - do not construct one with inline credentials.
 */
final class SakarMqttConfigFactory {

    private static final String TOPIC_PREFIX = "sakar";
    private static final int KEEP_ALIVE_SECONDS = 60;
    private static final int QOS = 1;
    private static final int HEARTBEAT_INTERVAL_SECONDS = 30;
    private static final int TELEMETRY_INTERVAL_SECONDS = 60;
    private static final int MAX_RECONNECT_DELAY_MILLIS = 128_000;
    private static final int OFFLINE_QUEUE_CAPACITY = 200;

    private SakarMqttConfigFactory() {
    }

    static SakarMqttConfig fromBuildConfig() {
        AgentIdentity identity = new AgentIdentity(
                BuildConfig.SAKAR_ORGANIZATION_ID,
                BuildConfig.SAKAR_SITE_ID,
                BuildConfig.SAKAR_ROBOT_ID,
                BuildConfig.SAKAR_AGENT_ID);
        return new SakarMqttConfig(
                BuildConfig.SAKAR_MQTT_BROKER_URL,
                TOPIC_PREFIX,
                identity,
                BuildConfig.SAKAR_MQTT_USERNAME,
                BuildConfig.SAKAR_MQTT_PASSWORD,
                KEEP_ALIVE_SECONDS,
                QOS,
                HEARTBEAT_INTERVAL_SECONDS,
                TELEMETRY_INTERVAL_SECONDS,
                MAX_RECONNECT_DELAY_MILLIS,
                OFFLINE_QUEUE_CAPACITY);
    }
}
