package com.sakarrobotics.cloud.cleaning;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sakarrobotics.cloud.robot.registry.Robot;
import com.sakarrobotics.cloud.task.RobotTask;

import lombok.RequiredArgsConstructor;

/**
 * Real, non-fabricated cleaning history (Roadmap Phase 6/9). This table has
 * two writers: {@link #recordFromCompletedTask}, called by {@code
 * RobotTaskService} the moment a {@code CLEANING}-type task is marked
 * {@code COMPLETED} — so a row here always corresponds to a task the
 * caller actually created and completed through the real task API, never a
 * fabricated cleaning run — and {@link #recordFromKeenonHistory} (Keenon
 * read-only cleaning-history sync slice), which appends rows read directly
 * from Keenon's own {@code clean/log/list}, independent of whether Sakar
 * ever dispatched the underlying run itself (e.g. runs started from the
 * robot's own tablet, or before this Sakar robot record even existed).
 * {@link CleaningSession#getVendorReference()} and {@link
 * CleaningSession#getFailureReason()} were previously null-until-populated
 * placeholders (see the Keenon parity requirements document for why {@code
 * cleanModel} strings, not a fabricated {@code cleanModelId}, are the only
 * real vendor evidence) — {@link #recordFromKeenonHistory} is that real
 * population.
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

    /**
     * Appends one Keenon-sourced history row — never updates an existing one,
     * since a completed vendor cleaning run is an immutable historical fact
     * (unlike area/mode/back-point "current state" sync, which legitimately
     * has a mutable-until-vendor-says-otherwise model). Tenant ownership
     * ({@code organizationId}/{@code siteId}) is always derived from {@code
     * robot} — the vendor's own response carries no organization/site
     * concept at all, so there is nothing from Keenon to (and nothing here
     * ever does) trust instead.
     *
     * <p>{@code vendorReference} is both this row's traceability field
     * ({@link CleaningSession}'s own original purpose for it) and the
     * idempotency key — see {@code KeenonCleaningHistorySyncService} for why:
     * no per-record vendor identifier is documented or evidenced anywhere
     * for a single {@code clean/log/list} entry, so it is a deterministic
     * composite of every evidenced field for that entry rather than a
     * vendor-issued id.
     *
     * @return the newly created row, or empty if a row with this exact
     *         {@code vendorReference} already exists (already synced —
     *         skipped, not overwritten).
     */
    @Transactional
    public Optional<CleaningSession> recordFromKeenonHistory(Robot robot, Double areaSqMeters, Double efficiency,
            Long durationSeconds, String result, String failureReason, String snapshotUrl, String vendorReference) {
        if (cleaningSessionRepository.existsByVendorReference(vendorReference)) {
            return Optional.empty();
        }
        CleaningSession session = new CleaningSession();
        session.setRobotId(robot.getId());
        session.setSiteId(robot.getSiteId());
        session.setOrganizationId(robot.getOrganizationId());
        session.setAreaSqMeters(areaSqMeters);
        session.setEfficiency(efficiency);
        session.setDurationSeconds(durationSeconds);
        session.setResult(result);
        session.setFailureReason(failureReason);
        session.setSnapshotUrl(snapshotUrl);
        session.setVendorReference(vendorReference);
        return Optional.of(cleaningSessionRepository.save(session));
    }
}
