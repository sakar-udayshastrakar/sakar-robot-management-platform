package com.sakarrobotics.c40agent.api.mqtt;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sakarrobotics.c40agent.api.mqtt.dto.AckPayload;
import com.sakarrobotics.c40agent.api.mqtt.dto.ErrorPayload;
import com.sakarrobotics.c40agent.api.mqtt.dto.EventPayload;
import com.sakarrobotics.c40agent.api.mqtt.dto.HeartbeatPayload;
import com.sakarrobotics.c40agent.api.mqtt.dto.PresencePayload;
import com.sakarrobotics.c40agent.api.mqtt.dto.TelemetryFieldReading;
import com.sakarrobotics.c40agent.api.mqtt.dto.TelemetryPayload;
import com.sakarrobotics.c40agent.logging.SdkCallLogger;

/**
 * The Sakar Robot Agent's MQTT communication layer (Phase 3 Part 6):
 * <pre>
 * MQTT Client (this class)
 *     -&gt; Agent Communication Layer (this module, :api)
 *     -&gt; Robot Controller / Telemetry Provider (supplied by the app module, via {@link TelemetrySnapshotProvider})
 *     -&gt; PeanutSdkBridge (:sdk - never touched directly from here)
 * </pre>
 * This class never imports {@code com.keenon.*} and never depends on
 * {@code :sdk}/{@code :robot} - see {@link TelemetrySnapshotProvider}'s
 * Javadoc for how that boundary is preserved.
 *
 * <p>Same MQTT client library/version as the Sakar Cloud backend (Eclipse
 * Paho {@code org.eclipse.paho.client.mqttv3} 1.2.5). Uses a single
 * background thread for the (blocking) {@code connect()} call so it is
 * always safe to invoke {@link #connect()} from {@code Application.onCreate()}
 * on the Android main thread.
 */
public final class AgentMqttClient {

    private static final String SCHEMA_VERSION = "1.0";

    private final SakarMqttConfig config;
    private final AgentMqttTopics topics;
    private final AgentMqttListener listener;
    private final Gson gson = new Gson();
    private final AtomicLong sequence = new AtomicLong();
    private final BoundedOfflineQueue<QueuedPublish> offlineQueue;
    private final ExecutorService connectExecutor = Executors.newSingleThreadExecutor(daemonThreadFactory("sakar-mqtt-connect"));

    private volatile MqttClient client;
    private volatile boolean stopped;

    public AgentMqttClient(SakarMqttConfig config, AgentMqttListener listener) {
        this.config = config;
        this.listener = listener != null ? listener : new AgentMqttListener() {
        };
        this.topics = new AgentMqttTopics(config.getTopicPrefix(), config.getIdentity());
        this.offlineQueue = new BoundedOfflineQueue<>(config.getOfflineQueueCapacity());
    }

    /** Non-blocking - the actual (blocking) Paho connect runs on a background thread. */
    public void connect() {
        if (!config.isConfigured()) {
            SdkCallLogger.getInstance().logError("AgentMqttClient.connect", "n/a", -1, "MQTT is not configured (missing broker URL or identity) - not connecting");
            return;
        }
        stopped = false;
        connectExecutor.submit(this::connectBlocking);
    }

    public void disconnect() {
        stopped = true;
        MqttClient current = client;
        if (current == null) {
            return;
        }
        try {
            if (current.isConnected()) {
                publishRetained(topics.presence(), presenceEnvelope(PresencePayload.OFFLINE));
                current.disconnect();
            }
        } catch (MqttException ex) {
            SdkCallLogger.getInstance().logError("AgentMqttClient.disconnect", "n/a", ex.getReasonCode(), String.valueOf(ex.getMessage()));
        }
    }

    public void publishHeartbeat(String agentVersion, long uptimeSeconds, String connectionStatus) {
        HeartbeatPayload payload = new HeartbeatPayload(agentVersion, uptimeSeconds, connectionStatus);
        publish(topics.heartbeat(), MqttMessageType.HEARTBEAT, gson.toJsonTree(payload).getAsJsonObject(), false);
    }

    public void publishTelemetry(List<TelemetryFieldReading> readings) {
        if (readings == null || readings.isEmpty()) {
            return;
        }
        TelemetryPayload payload = new TelemetryPayload(readings);
        publish(topics.telemetry(), MqttMessageType.TELEMETRY, gson.toJsonTree(payload).getAsJsonObject(), false);
    }

    public void publishEvent(String eventType, String severity, String rawPayload) {
        EventPayload payload = new EventPayload(eventType, severity, rawPayload, Instant.now().toString());
        publish(topics.events(), MqttMessageType.EVENT, gson.toJsonTree(payload).getAsJsonObject(), false);
    }

    public void publishError(String errorCode, String severity, String source, String message, String sdkApi) {
        ErrorPayload payload = new ErrorPayload(errorCode, severity, source, message, sdkApi, Instant.now().toString());
        publish(topics.errors(), MqttMessageType.ERROR, gson.toJsonTree(payload).getAsJsonObject(), false);
    }

    // ---------------------------------------------------------------

    private void connectBlocking() {
        try {
            MqttClient newClient = new MqttClient(config.getBrokerUrl(), clientIdFor(config.getIdentity()), new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setMaxReconnectDelay(config.getMaxReconnectDelayMillis()); // Paho's own bounded exponential backoff
            options.setKeepAliveInterval(config.getKeepAliveSeconds());
            options.setConnectionTimeout(30);
            if (config.getMqttUsername() != null && !config.getMqttUsername().isEmpty()) {
                options.setUserName(config.getMqttUsername());
                options.setPassword(config.getMqttPassword() != null ? config.getMqttPassword().toCharArray() : new char[0]);
            }
            // TLS (Phase 3 Security Hardening): an "ssl://" brokerUrl alone already gets real,
            // platform-default CA validation from Paho - never trust-all, no code required. This
            // block only matters for a private/self-signed broker CA.
            if (config.getTlsTrustStorePath() != null && !config.getTlsTrustStorePath().isEmpty()) {
                java.util.Properties sslProperties = new java.util.Properties();
                sslProperties.setProperty("com.ibm.ssl.trustStore", config.getTlsTrustStorePath());
                sslProperties.setProperty("com.ibm.ssl.trustStorePassword",
                        config.getTlsTrustStorePassword() != null ? config.getTlsTrustStorePassword() : "");
                options.setSSLProperties(sslProperties);
            }
            // Last Will and Testament: the broker publishes this, retained, if this client
            // disconnects uncleanly - the primary offline-detection mechanism (Phase 3 Part 12,
            // SAKAR_ROBOT_PLATFORM_ARCHITECTURE.md §4).
            JsonObject willPayload = gson.toJsonTree(new PresencePayload(PresencePayload.OFFLINE)).getAsJsonObject();
            options.setWill(topics.presence(), gson.toJson(buildPresenceEnvelope(willPayload)).getBytes(), config.getQos(), true);

            newClient.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    listener.onConnected(reconnect);
                    try {
                        newClient.subscribe(topics.ack(), config.getQos(), (topic, message) -> handleAck(message));
                    } catch (MqttException ex) {
                        SdkCallLogger.getInstance().logError("AgentMqttClient.subscribe", topics.ack(), ex.getReasonCode(), String.valueOf(ex.getMessage()));
                    }
                    publishRetained(topics.presence(), presenceEnvelope(PresencePayload.ONLINE));
                    flushOfflineQueue();
                }

                @Override
                public void connectionLost(Throwable cause) {
                    listener.onConnectionLost(cause);
                    if (!stopped) {
                        listener.onReconnecting(); // automaticReconnect is already retrying underneath
                    }
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    // No-op: the ack subscription above uses its own per-topic listener.
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                }
            });

            client = newClient;
            newClient.connect(options);
            SdkCallLogger.getInstance().logSuccess("AgentMqttClient.connect", config.getBrokerUrl(), "connected");
        } catch (MqttException ex) {
            SdkCallLogger.getInstance().logError("AgentMqttClient.connect", config.getBrokerUrl(), ex.getReasonCode(), String.valueOf(ex.getMessage()));
            // automaticReconnect only takes over after an INITIAL successful connect; an initial
            // failure here does not retry itself - callers may call connect() again.
        }
    }

    private void handleAck(MqttMessage message) {
        try {
            MqttEnvelope envelope = gson.fromJson(new String(message.getPayload()), MqttEnvelope.class);
            AckPayload ack = gson.fromJson(envelope.getPayload(), AckPayload.class);
            listener.onAckReceived(ack);
        } catch (RuntimeException malformed) {
            SdkCallLogger.getInstance().logError("AgentMqttClient.handleAck", "n/a", -1, "malformed ack: " + malformed.getMessage());
        }
    }

    private void publish(String topic, MqttMessageType type, JsonObject payload, boolean retained) {
        MqttEnvelope envelope = new MqttEnvelope(SCHEMA_VERSION, UUID.randomUUID().toString(), config.getIdentity().getRobotId(),
                config.getIdentity().getAgentId(), Instant.now().toString(), type, sequence.incrementAndGet(), payload);
        byte[] bytes = gson.toJson(envelope).getBytes();
        if (!tryPublish(topic, bytes, retained)) {
            offlineQueue.offer(new QueuedPublish(topic, bytes, retained));
        }
    }

    private void publishRetained(String topic, MqttEnvelope envelope) {
        tryPublish(topic, gson.toJson(envelope).getBytes(), true);
    }

    private boolean tryPublish(String topic, byte[] bytes, boolean retained) {
        MqttClient current = client;
        if (current == null || !current.isConnected()) {
            return false;
        }
        try {
            MqttMessage message = new MqttMessage(bytes);
            message.setQos(config.getQos());
            message.setRetained(retained);
            current.publish(topic, message);
            return true;
        } catch (MqttException ex) {
            SdkCallLogger.getInstance().logError("AgentMqttClient.publish", topic, ex.getReasonCode(), String.valueOf(ex.getMessage()));
            return false;
        }
    }

    private void flushOfflineQueue() {
        List<QueuedPublish> queued = offlineQueue.drainAll();
        for (QueuedPublish item : queued) {
            if (!tryPublish(item.topic, item.payload, item.retained)) {
                offlineQueue.offer(item); // still offline somehow - put it back rather than drop it silently
            }
        }
    }

    private MqttEnvelope presenceEnvelope(String status) {
        return buildPresenceEnvelope(gson.toJsonTree(new PresencePayload(status)).getAsJsonObject());
    }

    private MqttEnvelope buildPresenceEnvelope(JsonObject presencePayloadJson) {
        return new MqttEnvelope(SCHEMA_VERSION, UUID.randomUUID().toString(), config.getIdentity().getRobotId(),
                config.getIdentity().getAgentId(), Instant.now().toString(), MqttMessageType.PRESENCE,
                sequence.incrementAndGet(), presencePayloadJson);
    }

    private static String clientIdFor(AgentIdentity identity) {
        return "sakar-agent-" + identity.getRobotId();
    }

    private static ThreadFactory daemonThreadFactory(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    private static final class QueuedPublish {
        private final String topic;
        private final byte[] payload;
        private final boolean retained;

        private QueuedPublish(String topic, byte[] payload, boolean retained) {
            this.topic = topic;
            this.payload = payload;
            this.retained = retained;
        }
    }
}
