package com.sakarrobotics.cloud.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sakarrobotics.cloud.mqtt.dto.AckPayload;
import com.sakarrobotics.cloud.mqtt.dto.HeartbeatPayload;

import tools.jackson.databind.ObjectMapper;

/**
 * The Paho-callback adapter, tested with a mocked gateway/service — no
 * broker involved (Phase 3 Part 16: "message parsing", "ACK generation").
 */
@ExtendWith(MockitoExtension.class)
class MqttInboundListenerTest {

    private final MqttProperties properties = new MqttProperties();
    private final MqttTopicResolver topicResolver = new MqttTopicResolver(properties);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private MqttInboundMessageService inboundMessageService;
    @Mock
    private MqttGatewayService gatewayService;
    @Mock
    private MqttLifecycleLogger lifecycleLogger;

    // Constructed in @BeforeEach, not as a field initializer: @Mock fields are injected by
    // MockitoExtension's TestInstancePostProcessor, which runs AFTER instance field initializers —
    // a field initializer referencing them here would always see null.
    private MqttInboundListener listener;

    @BeforeEach
    void setUp() {
        listener = new MqttInboundListener(topicResolver, inboundMessageService, gatewayService, properties, lifecycleLogger, objectMapper);
    }

    @Test
    void validMessage_isHandedToTheServiceAndAnAcceptedAckIsPublished() throws Exception {
        UUID org = UUID.randomUUID();
        UUID robot = UUID.randomUUID();
        String messageId = UUID.randomUUID().toString();
        MqttEnvelope envelope = new MqttEnvelope("1.0", messageId, robot, "agent-1", Instant.now(),
                MqttMessageType.HEARTBEAT, 1, objectMapper.valueToTree(new HeartbeatPayload("0.1.0", 5, "CONNECTED")));
        when(inboundMessageService.handle(any(ParsedMqttTopic.class), any(MqttEnvelope.class))).thenReturn(MqttIngestResult.ok());

        String topic = topicResolver.topic(org, null, robot, MqttTopicKind.HEARTBEAT);
        listener.messageArrived(topic, new MqttMessage(objectMapper.writeValueAsBytes(envelope)));

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(gatewayService).publish(topicCaptor.capture(), payloadCaptor.capture(), eq(properties.getQos()), eq(false));
        assertThat(topicCaptor.getValue()).isEqualTo(topicResolver.topic(org, null, robot, MqttTopicKind.ACK));
        AckPayload ack = objectMapper.readValue(payloadCaptor.getValue(), AckPayload.class);
        assertThat(ack.accepted()).isTrue();
        assertThat(ack.messageId()).isEqualTo(messageId);
    }

    @Test
    void rejectedResult_publishesARejectedAckWithTheReasonCode() throws Exception {
        UUID robot = UUID.randomUUID();
        MqttEnvelope envelope = new MqttEnvelope("1.0", UUID.randomUUID().toString(), robot, "agent-1", Instant.now(),
                MqttMessageType.HEARTBEAT, 1, objectMapper.valueToTree(new HeartbeatPayload("0.1.0", 5, "CONNECTED")));
        when(inboundMessageService.handle(any(ParsedMqttTopic.class), any(MqttEnvelope.class)))
                .thenReturn(MqttIngestResult.rejected(MqttRejectionReason.UNKNOWN_ROBOT, "not registered"));

        String topic = topicResolver.topic(UUID.randomUUID(), null, robot, MqttTopicKind.HEARTBEAT);
        listener.messageArrived(topic, new MqttMessage(objectMapper.writeValueAsBytes(envelope)));

        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(gatewayService).publish(anyString(), payloadCaptor.capture(), eq(properties.getQos()), eq(false));
        AckPayload ack = objectMapper.readValue(payloadCaptor.getValue(), AckPayload.class);
        assertThat(ack.accepted()).isFalse();
        assertThat(ack.reason()).isEqualTo("UNKNOWN_ROBOT");
    }

    @Test
    void malformedJsonPayload_isRejectedWithoutEverCallingTheInboundService() {
        UUID robot = UUID.randomUUID();
        String topic = topicResolver.topic(UUID.randomUUID(), null, robot, MqttTopicKind.TELEMETRY);

        listener.messageArrived(topic, new MqttMessage("{ not valid json".getBytes()));

        verify(inboundMessageService, never()).handle(any(), any());
        verify(gatewayService).publish(anyString(), any(byte[].class), eq(properties.getQos()), eq(false));
    }

    @Test
    void oversizedPayload_isRejectedWithoutEverParsingItOrCallingTheInboundService() throws Exception {
        UUID robot = UUID.randomUUID();
        String topic = topicResolver.topic(UUID.randomUUID(), null, robot, MqttTopicKind.TELEMETRY);
        // properties.getMaxPayloadSizeBytes() defaults to 65536 - build a JSON-shaped but oversized payload.
        byte[] oversized = new byte[properties.getMaxPayloadSizeBytes() + 1];
        java.util.Arrays.fill(oversized, (byte) 'a');

        listener.messageArrived(topic, new MqttMessage(oversized));

        verify(inboundMessageService, never()).handle(any(), any());
        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(gatewayService).publish(anyString(), payloadCaptor.capture(), eq(properties.getQos()), eq(false));
        AckPayload ack = objectMapper.readValue(payloadCaptor.getValue(), AckPayload.class);
        assertThat(ack.accepted()).isFalse();
        assertThat(ack.reason()).isEqualTo("PAYLOAD_TOO_LARGE");
    }

    @Test
    void malformedTopic_isIgnoredWithoutThrowing() {
        listener.messageArrived("not-a-sakar-topic", new MqttMessage("{}".getBytes()));

        verify(inboundMessageService, never()).handle(any(), any());
        verify(gatewayService, never()).publish(anyString(), any(byte[].class), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyBoolean());
    }
}
