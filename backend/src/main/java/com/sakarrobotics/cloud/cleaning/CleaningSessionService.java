package com.sakarrobotics.cloud.cleaning;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sakarrobotics.cloud.task.RobotTask;

import lombok.RequiredArgsConstructor;

/**
 * Real, non-fabricated cleaning history (Roadmap Phase 6/9). This table has
 * exactly one writer — {@link #recordFromCompletedTask}, called by {@code
 * RobotTaskService} the moment a {@code CLEANING}-type task is marked
 * {@code COMPLETED} — so a row here always corresponds to a task the
 * caller actually created and completed through the real task API, never a
 * fabricated cleaning run. There is deliberately no dispatch/write path
 * for arbitrary cleaning "results" the way {@code cleanModel}/vendor
 * receipts would require; {@link CleaningSession#getVendorReference()} and
 * {@link CleaningSession#getFailureReason()} remain {@code null} until a
 * real vendor integration populates them (see the Keenon parity
 * requirements document for why {@code cleanModel} strings, not a
 * fabricated {@code cleanModelId}, are the only real vendor evidence).
 */
@Service
@RequiredArgsConstructor
public class CleaningSessionService {

    private final CleaningSessionRepository cleaningSessionRepository;

    public Page<CleaningSession> listByRobot(UUID robotId, int page, int pageSize) {
        return cleaningSessionRepository.findByRobotIdOrderByIdDesc(robotId, PageRequest.of(page, pageSize));
    }

    @Transactional
    public CleaningSession recordFromCompletedTask(RobotTask task, UUID siteId) {
        CleaningSession session = new CleaningSession();
        session.setRobotId(task.getRobotId());
        session.setSiteId(siteId);
        session.setOrganizationId(task.getOrganizationId());
        session.setTaskId(task.getId());
        session.setStartedAt(task.getCreatedAt());
        Instant endedAt = Instant.now();
        session.setEndedAt(endedAt);
        session.setDurationSeconds(Duration.between(task.getCreatedAt(), endedAt).getSeconds());
        session.setResult("COMPLETED");
        return cleaningSessionRepository.save(session);
    }
}
