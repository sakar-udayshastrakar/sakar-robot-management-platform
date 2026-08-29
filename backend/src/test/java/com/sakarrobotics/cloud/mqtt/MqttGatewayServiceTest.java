package com.sakarrobotics.cloud.mqtt;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;

/**
 * Disabled-by-default behavior — no broker required (same "testable
 * without a running broker" guarantee the class has always documented).
 */
class MqttGatewayServiceTest {

    private final MqttProperties disabledProperties = new MqttProperties(); // enabled=false by default
    private final MqttGatewayService gateway = new MqttGatewayService(disabledProperties);

    @Test
    void ensureConnected_throwsIntegrationUnavailable_whenDisabled() {
        assertThatThrownBy(gateway::ensureConnected)
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(((ApiException) ex).getErrorCode())
                        .isEqualTo(SakarErrorCode.INTEGRATION_UNAVAILABLE));
    }

    @Test
    void publish_throwsIntegrationUnavailable_whenDisabled() {
        assertThatThrownBy(() -> gateway.publish("sakar/x/_/y/telemetry", "{}".getBytes(), 1, false))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(((ApiException) ex).getErrorCode())
                        .isEqualTo(SakarErrorCode.INTEGRATION_UNAVAILABLE));
    }
}
