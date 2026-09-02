package com.sakarrobotics.cloud.srels.dto;

import java.time.Instant;
import java.util.UUID;

import com.sakarrobotics.cloud.srels.ApplicationLog;

public record ApplicationLogResponse(
        Long id,
        String source,
        UUID robotId,
        String level,
        String message,
        String context,
        Instant createdAt) {

    public static ApplicationLogResponse from(ApplicationLog log) {
        return new ApplicationLogResponse(log.getId(), log.getSource(), log.getRobotId(), log.getLevel(),
                log.getMessage(), log.getContext(), log.getCreatedAt());
    }
}
