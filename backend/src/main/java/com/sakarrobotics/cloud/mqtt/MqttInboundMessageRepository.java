package com.sakarrobotics.cloud.mqtt;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MqttInboundMessageRepository extends JpaRepository<MqttInboundMessage, Long> {

    Optional<MqttInboundMessage> findByRobotIdAndMessageId(UUID robotId, String messageId);
}
