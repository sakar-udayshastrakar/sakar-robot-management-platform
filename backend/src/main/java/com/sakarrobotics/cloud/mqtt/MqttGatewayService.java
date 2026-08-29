package com.sakarrobotics.cloud.mqtt;

import java.util.Properties;
import java.util.UUID;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;

/**
 * Robot-facing MQTT gateway (Master Requirements Part 15/23).
 *
 * <p>Connection lifecycle (this class) is deliberately kept separate from
 * subscription/ingestion wiring ({@link MqttSubscriptionManager}) and
 * message routing ({@link MqttInboundListener}) — this class only knows
 * how to lazily connect, publish, and disconnect. The client connects
 * lazily, only when {@link MqttProperties#isEnabled()} is true and a
 * caller invokes {@link #ensureConnected()} (still never called
 * automatically by this class itself or by any test — {@link
 * MqttSubscriptionManager} is the one production caller, itself gated on
 * the same {@code enabled} flag).
 */
@Service
@RequiredArgsConstructor
public class MqttGatewayService {

    private static final Logger log = LoggerFactory.getLogger(MqttGatewayService.class);

    private final MqttProperties properties;
    private volatile MqttClient client;

    /** Lazily connects. Never called during application startup or by any test — see class Javadoc. */
    public synchronized MqttClient ensureConnected() {
        if (!properties.isEnabled()) {
            throw new ApiException(SakarErrorCode.INTEGRATION_UNAVAILABLE, "MQTT is disabled in this environment");
        }
        if (client != null && client.isConnected()) {
            return client;
        }
        try {
            String clientId = properties.getClientIdPrefix() + UUID.randomUUID();
            MqttClient newClient = new MqttClient(properties.getBrokerUrl(), clientId, new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
                options.setUserName(properties.getUsername());
                options.setPassword(properties.getPassword() != null ? properties.getPassword().toCharArray() : new char[0]);
            }
            // TLS (Phase 3 Security Hardening): a "ssl://" broker URL is enough on its own — Paho
            // validates the broker's certificate against the JVM default trust store by default
            // (real CA validation, never trust-all/skip-verify — no such option exists anywhere in
            // this class). SSLProperties are only needed for a private/self-signed CA.
            if (properties.getTlsTrustStorePath() != null && !properties.getTlsTrustStorePath().isBlank()) {
                Properties sslProperties = new Properties();
                sslProperties.setProperty("com.ibm.ssl.trustStore", properties.getTlsTrustStorePath());
                sslProperties.setProperty("com.ibm.ssl.trustStorePassword",
                        properties.getTlsTrustStorePassword() != null ? properties.getTlsTrustStorePassword() : "");
                options.setSSLProperties(sslProperties);
            }
            newClient.connect(options);
            this.client = newClient;
            log.info("Connected to MQTT broker as {}", clientId);
            return newClient;
        } catch (MqttException ex) {
            throw new ApiException(SakarErrorCode.INTEGRATION_UNAVAILABLE, "Could not connect to MQTT broker", ex);
        }
    }

    /** Publishes a message, connecting first if necessary. Throws {@code INTEGRATION_UNAVAILABLE} if MQTT is disabled. */
    public void publish(String topic, byte[] payload, int qos, boolean retained) {
        MqttClient connected = ensureConnected();
        try {
            MqttMessage message = new MqttMessage(payload);
            message.setQos(qos);
            message.setRetained(retained);
            connected.publish(topic, message);
        } catch (MqttException ex) {
            throw new ApiException(SakarErrorCode.INTEGRATION_UNAVAILABLE, "Could not publish to MQTT broker", ex);
        }
    }

    @PreDestroy
    public void shutdown() {
        try {
            if (client != null && client.isConnected()) {
                client.disconnect();
            }
        } catch (MqttException ex) {
            log.warn("Error disconnecting MQTT client", ex);
        }
    }
}
