package com.sakarrobotics.cloud.srels;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sakarrobotics.cloud.mqtt.dto.EventPayload;

import lombok.RequiredArgsConstructor;

/**
 * Populates {@code robot_events} from an inbound MQTT {@code EVENT}
 * envelope (Phase 3) — the ingestion path {@link RobotEvent}'s Javadoc
 * previously described as "nothing in this codebase writes a row here
 * yet."
 */
@Service
@RequiredArgsConstructor
public class RobotEventIngestionService {

    private final RobotEventRepository robotEventRepository;

    @Transactional
    public void record(UUID robotId, EventPayload payload) {
        RobotEvent event = new RobotEvent();
        event.setRobotId(robotId);
        event.setEventType(payload.eventType());
        event.setSeverity(payload.severity());
        event.setPayload(payload.payload());
        event.setOccurredAt(payload.occurredAt());
        robotEventRepository.save(event);
    }
}
