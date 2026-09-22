package com.sakarrobotics.cloud.integration.keenon;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Central, wall-clock-aligned entry point for automatic Keenon data
 * synchronization ("Automatic Keenon Data Sync — Every 15 Minutes"). This
 * is a thin orchestration layer only — it invents no new Keenon API call,
 * no new upsert logic, and no new authentication: every category it
 * touches already has its own real, tested, independently documented sync
 * service/scheduler ({@link KeenonStatusSyncService}, {@link
 * KeenonAreaSyncScheduler}, {@link KeenonCleaningModeSyncScheduler}, {@link
 * KeenonBackPointSyncScheduler}, {@link KeenonCleaningHistorySyncScheduler},
 * {@link KeenonMapMetadataSyncScheduler}, {@link KeenonMapPointSyncScheduler},
 * {@link KeenonMapImageSyncScheduler}). This class only calls each one's
 * existing, unmodified, public {@code syncAll()} method in sequence, on one
 * shared 15-minute cron trigger, instead of each category continuing to
 * need its own separately-enabled schedule to achieve the same cadence.
 *
 * <p><strong>Deliberately excludes Keenon robot discovery</strong> ({@link
 * KeenonRobotSyncService}): unlike every category above, discovering
 * brand-new robots requires an explicit {@code (organizationId, storeId)}
 * pair that is never persisted anywhere generically — it is supplied only
 * via an explicit, human-triggered {@code POST /api/v1/keenon/robots/sync}
 * call. This is the exact same reason {@link KeenonAreaSyncScheduler}/
 * {@link KeenonCleaningHistorySyncScheduler} never invent a {@code storeId}
 * for a robot that has never been manually synced — auto-discovering new
 * robots every 15 minutes would mean guessing a store id, which this class
 * does not do.
 *
 * <p>Each of the 8 categories above keeps its own independent {@code
 * enabled}/{@code interval-ms} configuration in {@code application.yml}
 * completely unchanged (still defaulting to {@code false}) — this
 * orchestrator does not touch, require, or depend on any of those flags. It
 * calls their public {@code syncAll()} methods directly. An operator who
 * separately re-enables one of those individual schedulers (e.g. for
 * isolated testing) will see that category run on both its own schedule
 * and this one — that pre-existing possible combination is not prevented
 * here, since doing so would mean coupling this class's own execution to
 * eight independent flags for a rare, deliberately-opt-in edge case.
 *
 * <p><strong>Overlap protection:</strong> this application enables
 * scheduling via a single {@code @EnableScheduling} with no custom {@code
 * TaskScheduler} bean and no {@code spring.task.scheduling.pool.size}
 * override anywhere — Spring Boot's default is a single-threaded scheduler,
 * so two {@code @Scheduled} invocations (of this method or any other in the
 * process) can never run concurrently on the scheduling thread. No
 * ShedLock or other distributed-locking dependency exists anywhere in this
 * project, matching its single-instance deployment topology (see {@code
 * backend/docker-compose.yml}). The {@link #running} guard below exists for
 * the one path the scheduler thread alone does not cover — a manual trigger
 * arriving on an HTTP request thread (see {@code
 * KeenonSyncOrchestrationController}) while a scheduled cycle is still
 * running — and is intentionally the smallest possible safeguard (a single
 * in-process flag), not a distributed lock, since nothing about this
 * deployment's architecture currently requires one.
 */
@Service
@RequiredArgsConstructor
public class KeenonSyncOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(KeenonSyncOrchestrationService.class);

    private final KeenonProperties keenonProperties;
    private final KeenonStatusSyncService keenonStatusSyncService;
    private final KeenonAreaSyncScheduler keenonAreaSyncScheduler;
    private final KeenonCleaningModeSyncScheduler keenonCleaningModeSyncScheduler;
    private final KeenonBackPointSyncScheduler keenonBackPointSyncScheduler;
    private final KeenonCleaningHistorySyncScheduler keenonCleaningHistorySyncScheduler;
    private final KeenonMapMetadataSyncScheduler keenonMapMetadataSyncScheduler;
    private final KeenonMapPointSyncScheduler keenonMapPointSyncScheduler;
    private final KeenonMapImageSyncScheduler keenonMapImageSyncScheduler;

    /**
     * Defaults to {@code true} — unlike every individual category flag above
     * (which default {@code false}) — because "Sakar Cloud must
     * automatically pull the latest Keenon data every 15 minutes" is the
     * actual product requirement this class exists to satisfy, not an
     * opt-in extra. The real safety gate remains {@link
     * KeenonProperties#isEnabled()} (the top-level {@code
     * sakar.integration.keenon.enabled} flag): with no real Keenon
     * credentials configured, this method still runs on schedule but every
     * category call underneath fails fast and harmlessly, exactly like any
     * other Keenon feature invoked without credentials.
     */
    @Value("${sakar.integration.keenon.sync-orchestration.enabled:true}")
    private boolean enabled;

    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Wall-clock-aligned to :00/:15/:30/:45 every hour — a cron trigger, not
     * {@code fixedDelay}, specifically because only a cron expression
     * guarantees alignment to fixed clock boundaries regardless of
     * application start time.
     */
    @Scheduled(cron = "${sakar.integration.keenon.sync-orchestration.cron:0 0,15,30,45 * * * *}")
    public void scheduledSync() {
        if (enabled && keenonProperties.isEnabled()) {
            syncAll();
        }
    }

    /**
     * The single sync-cycle entry point — used by the scheduler above and
     * by the manual trigger ({@code KeenonSyncOrchestrationController}).
     * Never throws: every category is isolated in its own try/catch so one
     * category's failure never prevents the rest of the cycle from running,
     * mirroring the "one robot's failure never aborts the batch" convention
     * every category's own scheduler already applies one level down. Never
     * logs a credential, secret, or token — this class never touches one;
     * every category call below is a zero-argument method reference into
     * code that already owns its own authentication.
     */
    public SyncCycleResult syncAll() {
        if (!running.compareAndSet(false, true)) {
            log.warn("KEENON_SYNC_SKIPPED — a previous sync cycle is still running; this trigger was skipped, not queued.");
            Instant now = Instant.now();
            return new SyncCycleResult(now, now, Map.of(), "SKIPPED_ALREADY_RUNNING", false);
        }

        Instant start = Instant.now();
        log.info("KEENON_SYNC_START at {}", start);
        Map<String, String> categoryStatus = new LinkedHashMap<>();
        try {
            runCategory(categoryStatus, "robotStatus", keenonStatusSyncService::syncAll);
            runCategory(categoryStatus, "areas", keenonAreaSyncScheduler::syncAll);
            runCategory(categoryStatus, "cleaningModes", keenonCleaningModeSyncScheduler::syncAll);
            runCategory(categoryStatus, "backPoints", keenonBackPointSyncScheduler::syncAll);
            runCategory(categoryStatus, "cleaningHistory", keenonCleaningHistorySyncScheduler::syncAll);
            runCategory(categoryStatus, "mapMetadata", keenonMapMetadataSyncScheduler::syncAll);
            runCategory(categoryStatus, "mapPoints", keenonMapPointSyncScheduler::syncAll);
            runCategory(categoryStatus, "mapImages", keenonMapImageSyncScheduler::syncAll);
        } finally {
            running.set(false);
        }

        Instant end = Instant.now();
        boolean anyFailed = categoryStatus.values().stream().anyMatch(status -> !"SUCCESS".equals(status));
        String overallStatus = anyFailed ? "SUCCESS_WITH_WARNINGS" : "SUCCESS";
        log.info("KEENON_SYNC_COMPLETE at {} (duration {}ms) status={} categories={}",
                end, Duration.between(start, end).toMillis(), overallStatus, categoryStatus);

        return new SyncCycleResult(start, end, categoryStatus, overallStatus, true);
    }

    private void runCategory(Map<String, String> categoryStatus, String categoryName, Runnable sync) {
        try {
            sync.run();
            categoryStatus.put(categoryName, "SUCCESS");
        } catch (Exception ex) {
            // Defensive: one category's unexpected failure must never abort the rest of the
            // cycle — mirrors the "one robot never aborts the batch" convention each
            // category's own scheduler already applies one level down. Per-robot/per-record
            // fetched/created/updated counts are already logged by each category's own
            // scheduler/service (e.g. "Keenon cleaning-history sync: recorded N new history
            // row(s) for robot X") — deliberately not re-counted or duplicated here.
            log.warn("KEENON_SYNC category '{}' failed: {}", categoryName, ex.getMessage());
            categoryStatus.put(categoryName, "FAILED");
        }
    }

    /**
     * Never carries a credential, secret, or token — only category names and
     * a SUCCESS/FAILED status string per category, safe to serialize as an
     * API response (see {@code KeenonSyncOrchestrationController}).
     */
    public record SyncCycleResult(Instant startedAt, Instant completedAt, Map<String, String> categoryStatus,
            String overallStatus, boolean ran) {
    }
}
