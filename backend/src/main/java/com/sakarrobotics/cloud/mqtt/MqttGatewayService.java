package com.sakarrobotics.cloud.mqtt;

import java.util.UUID;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;

/**
 * Robot-facing MQTT gateway skeleton (Master Requirements Part 15/23).
 *
 * <p><strong>Phase 1 scope: connection lifecycle only.</strong> No
 * telemetry ingestion, command publish, or topic ACL enforcement is wired
 * yet (Phase 2/5) — this class exists so the transport dependency, config,
 * and connect/disconnect lifecycle are in place and testable without
 * requiring a running broker (the client connects lazily, only when
 * {@link MqttProperties#isEnabled()} is true and a caller invokes
 * {@link #ensureConnected()}; nothing calls that automatically at startup).
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
            newClient.connect(options);
            this.client = newClient;
            log.info("Connected to MQTT broker as {}", clientId);
            return newClient;
        } catch (MqttException ex) {
            throw new ApiException(SakarErrorCode.INTEGRATION_UNAVAILABLE, "Could not connect to MQTT broker", ex);
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
