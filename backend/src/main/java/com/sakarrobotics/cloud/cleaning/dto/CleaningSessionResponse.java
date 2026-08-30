package com.sakarrobotics.cloud.cleaning.dto;

import java.time.Instant;
import java.util.UUID;

import com.sakarrobotics.cloud.cleaning.CleaningSession;

/**
 * {@code vendorReference}/{@code failureReason} deliberately never carry a
 * raw vendor code today (see {@link CleaningSession}'s own Javadoc) — this
 * response shape reserves the fields for when a real vendor-reconciled
 * write path exists, rather than inventing values now.
 */
public record CleaningSessionResponse(
        UUID id,
        UUID robotId,
        UUID siteId,
        UUID taskId,
        Instant startedAt,
        Instant endedAt,
        Long durationSeconds,
        Double areaSqMeters,
        Double efficiency,
        String result,
        String failureReason) {

    public static CleaningSessionResponse from(CleaningSession session) {
        return new CleaningSessionResponse(session.getId(), session.getRobotId(), session.getSiteId(),
                session.getTaskId(), session.getStartedAt(), session.getEndedAt(), session.getDurationSeconds(),
                session.getAreaSqMeters(), session.getEfficiency(), session.getResult(), session.getFailureReason());
    }
}
