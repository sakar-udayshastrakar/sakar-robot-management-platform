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
}
