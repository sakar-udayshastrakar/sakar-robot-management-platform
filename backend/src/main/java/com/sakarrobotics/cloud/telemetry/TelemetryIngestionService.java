package com.sakarrobotics.cloud.telemetry;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sakarrobotics.cloud.robot.adapter.dto.TelemetryReading;

import lombok.RequiredArgsConstructor;

/**
 * Populates {@code robot_telemetry} from an inbound MQTT
 * {@code TELEMETRY} envelope (Phase 3) — the ingestion path {@link
 * RobotTelemetry}'s Javadoc previously described as "Phase 2+, not
 * implemented."
 */
@Service
@RequiredArgsConstructor
public class TelemetryIngestionService {

    private final RobotTelemetryRepository robotTelemetryRepository;
    private final RobotStatusService robotStatusService;

    @Transactional
    public int ingest(UUID robotId, List<TelemetryReading> readings) {
        for (TelemetryReading reading : readings) {
            RobotTelemetry entity = new RobotTelemetry();
            entity.setRobotId(robotId);
            entity.setMetric(reading.metric());
            entity.setValueNumeric(reading.valueNumeric());
            entity.setValueText(reading.valueText());
            entity.setRecordedAt(reading.recordedAt());
            robotTelemetryRepository.save(entity);

            robotStatusService.applyKnownMetric(robotId, reading.metric(), reading.valueNumeric(), reading.valueText(), reading.recordedAt());
        }
        return readings.size();
    }
}
