package com.sakarrobotics.cloud.mqtt;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Turns the connection-lifecycle-only {@link MqttGatewayService} into a
 * working subscriber (Phase 3) — connects and subscribes to every inbound
 * topic filter once at startup, and re-subscribes automatically after a
 * connection drop.
 *
 * <p>Fires on {@link ApplicationReadyEvent} rather than {@code
 * @PostConstruct} so a broker-connection failure never blocks application
 * startup itself; gated on {@link MqttProperties#isEnabled()}, so a
 * deployment/test with MQTT disabled starts exactly as before Phase 3 (no
 * connection is ever attempted) — same policy {@link MqttGatewayService}
 * already documented.
 */
@Component
@RequiredArgsConstructor
class MqttSubscriptionManager implements ApplicationListener<ApplicationReadyEvent> {

    private final MqttProperties properties;
    private final MqttGatewayService gatewayService;
    private final MqttTopicResolver topicResolver;
    private final MqttInboundListener inboundListener;
    private final MqttLifecycleLogger lifecycleLogger;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            connectAndSubscribe();
        } catch (RuntimeException ex) {
            // A broker being unreachable at startup must not crash the backend — automaticReconnect
            // (set on the client in MqttGatewayService) will keep retrying with backoff.
            lifecycleLogger.error(null, "MQTT_CONNECT_FAILED", ex.getMessage());
        }
    }

    private void connectAndSubscribe() {
        MqttClient client = gatewayService.ensureConnected();
        // Registered only after the initial connect (ensureConnected() already connected
        // synchronously) — connectComplete below therefore only ever fires for RECONNECTS;
        // the initial subscription is issued explicitly right after this call.
        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                if (reconnect) {
                    lifecycleLogger.info(null, "MQTT_RECONNECTED", serverURI);
                    subscribeAll(client);
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                lifecycleLogger.warn(null, "MQTT_DISCONNECTED", cause != null ? String.valueOf(cause.getMessage()) : "unknown cause");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                // No-op: every subscription below is registered with its own IMqttMessageListener,
                // so the client-wide callback's messageArrived is never actually invoked by Paho.
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        });
        lifecycleLogger.info(null, "MQTT_CONNECTED", properties.getBrokerUrl());
        subscribeAll(client);
    }

    private void subscribeAll(MqttClient client) {
        for (String filter : topicResolver.inboundSubscriptionFilters()) {
            try {
                client.subscribe(filter, properties.getQos(), inboundListener);
            } catch (MqttException ex) {
                lifecycleLogger.error(null, "MQTT_SUBSCRIBE_FAILED", filter + ": " + ex.getMessage());
            }
        }
    }
}
