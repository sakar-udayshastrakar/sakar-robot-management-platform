package com.sakarrobotics.cloud.mqtt;

import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.stereotype.Component;

import com.sakarrobotics.cloud.mqtt.dto.AckPayload;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * Thin Paho-callback adapter (Phase 3): parses the topic and envelope,
 * hands off to {@link MqttInboundMessageService#handle} for every actual
 * decision, and publishes the resulting {@link AckPayload}. Intentionally
 * has almost no logic of its own — everything worth unit-testing lives in
 * {@link MqttInboundMessageService} and {@link MqttTopicResolver}, which
 * don't require a running broker to test.
 */
@Component
@RequiredArgsConstructor
class MqttInboundListener implements IMqttMessageListener {

    private final MqttTopicResolver topicResolver;
    private final MqttInboundMessageService inboundMessageService;
    private final MqttGatewayService gatewayService;
    private final MqttProperties properties;
    private final MqttLifecycleLogger lifecycleLogger;
    private final ObjectMapper objectMapper;

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        ParsedMqttTopic parsed;
        try {
            parsed = topicResolver.parse(topic);
        } catch (IllegalArgumentException malformedTopic) {
            lifecycleLogger.warn(null, "MALFORMED_MESSAGE_REJECTED", "unparseable topic: " + topic);
            return;
        }

        byte[] rawPayload = message.getPayload();
        if (rawPayload.length > properties.getMaxPayloadSizeBytes()) {
            // Rejected before any JSON parsing is attempted — the whole point of a size cap is to
            // never hand an oversized buffer to the parser in the first place (Phase 3 Security
            // Hardening — DoS/memory-exhaustion protection).
            lifecycleLogger.warn(parsed.robotId(), "PAYLOAD_TOO_LARGE_REJECTED",
                    rawPayload.length + " bytes exceeds sakar.mqtt.max-payload-size-bytes on " + topic);
            publishAck(parsed, AckPayload.rejected(null, parsed.robotId(), MqttRejectionReason.PAYLOAD_TOO_LARGE.name()));
            return;
        }

        MqttEnvelope envelope;
        try {
            envelope = objectMapper.readValue(rawPayload, MqttEnvelope.class);
        } catch (Exception malformedJson) {
            lifecycleLogger.warn(parsed.robotId(), "MALFORMED_MESSAGE_REJECTED", "invalid JSON envelope on " + topic);
            publishAck(parsed, AckPayload.rejected(null, parsed.robotId(), MqttRejectionReason.MALFORMED_MESSAGE.name()));
            return;
        }

        MqttIngestResult result = inboundMessageService.handle(parsed, envelope);
        AckPayload ack = result.accepted()
                ? AckPayload.accepted(envelope.messageId(), envelope.robotId())
                : AckPayload.rejected(envelope.messageId(), envelope.robotId(), result.reasonCode());
        publishAck(parsed, ack);
    }

    private void publishAck(ParsedMqttTopic parsed, AckPayload ack) {
        try {
            String ackTopic = topicResolver.topic(parsed.organizationId(), parsed.siteId(), parsed.robotId(), MqttTopicKind.ACK);
            gatewayService.publish(ackTopic, objectMapper.writeValueAsBytes(ack), properties.getQos(), false);
        } catch (Exception ex) {
            lifecycleLogger.error(parsed.robotId(), "ACK_PUBLISH_FAILED", ex.getMessage());
        }
    }
}
