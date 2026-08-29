package com.sakarrobotics.cloud.telemetry;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sakarrobotics.cloud.mqtt.dto.HeartbeatPayload;

import lombok.RequiredArgsConstructor;

/**
 * Drives {@code robot_status.online}/{@code last_seen_at} from the agent's
 * periodic heartbeat (Phase 3 Part 7, {@code SAKAR_ROBOT_PLATFORM_API_SPEC.md}
 * §2.2). Records agent health only — never fabricates robot-state fields
 * (battery, charging, ...) from a heartbeat; those come only from an
 * actual {@code TELEMETRY} message via {@link TelemetryIngestionService}.
 */
@Service
@RequiredArgsConstructor
public class HeartbeatService {

    private final RobotStatusService robotStatusService;

    @Transactional
    public RobotStatus record(UUID robotId, HeartbeatPayload payload, Instant timestamp) {
        return robotStatusService.markOnline(robotId, timestamp);
    }
}
