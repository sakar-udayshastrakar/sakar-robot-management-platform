package com.sakarrobotics.cloud.srels;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sakarrobotics.cloud.mqtt.dto.ErrorPayload;

import lombok.RequiredArgsConstructor;

/**
 * Populates {@code robot_errors} from an inbound MQTT {@code ERROR}
 * envelope (Phase 3) — the ingestion path {@link RobotError}'s Javadoc
 * previously described as "Phase 1 scope: schema/entity only."
 */
@Service
@RequiredArgsConstructor
public class RobotErrorIngestionService {

    private final RobotErrorRepository robotErrorRepository;

    @Transactional
    public void record(UUID robotId, ErrorPayload payload) {
        RobotError error = new RobotError();
        error.setRobotId(robotId);
        error.setErrorCode(payload.errorCode());
        error.setSeverity(payload.severity());
        error.setSource(payload.source());
        error.setMessage(payload.message());
        error.setSdkApi(payload.sdkApi());
        error.setOccurredAt(payload.occurredAt());
        robotErrorRepository.save(error);
    }
}
