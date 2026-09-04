package com.sakarrobotics.cloud.integration.keenon;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.robot.registry.AdapterType;
import com.sakarrobotics.cloud.robot.registry.Robot;
import com.sakarrobotics.cloud.robot.registry.RobotCapabilityService;
import com.sakarrobotics.cloud.robot.registry.RobotCapabilityType;
import com.sakarrobotics.cloud.robot.registry.RobotLifecycleStatus;
import com.sakarrobotics.cloud.robot.registry.RobotModel;
import com.sakarrobotics.cloud.robot.registry.RobotModelRepository;
import com.sakarrobotics.cloud.robot.registry.RobotRepository;

import lombok.RequiredArgsConstructor;

/**
 * Periodically appends new {@code cleaning_sessions} rows from Keenon's own
 * cleaning-log history — follows the exact "real, gated, wall-clock-driven
 * sweep, one robot's failure never aborts the rest" pattern as every other
 * Keenon sync scheduler in this codebase.
 *
 * <p>Like area sync (and unlike cleaning-mode/back-point sync), {@code
 * GET .../clean/log/list} needs a {@code storeId} that is not stored
 * anywhere on {@link Robot} itself — so this scheduler reuses exactly the
 * same bootstrap source {@link KeenonAreaSyncScheduler} already established:
 * only robots with an existing active {@link KeenonAreaMapping} (which
 * carries a real, previously-recorded {@code keenonStoreId}) are refreshed.
 * A robot that has never had an area sync is never touched here — no
 * storeId is ever invented.
 */
@Service
@RequiredArgsConstructor
public class KeenonCleaningHistorySyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(KeenonCleaningHistorySyncScheduler.class);

    private final KeenonAreaMappingRepository areaMappingRepository;
    private final RobotRepository robotRepository;
    private final RobotModelRepository robotModelRepository;
    private final RobotCapabilityService robotCapabilityService;
    private final KeenonCleaningHistorySyncService keenonCleaningHistorySyncService;

    @Value("${sakar.integration.keenon.cleaning-history-sync.enabled:false}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${sakar.integration.keenon.cleaning-history-sync.interval-ms:21600000}")
    public void scheduledSync() {
        if (enabled) {
            syncAll();
        }
    }

    public void syncAll() {
        Map<UUID, String> storeIdByRobotId = new LinkedHashMap<>();
        for (KeenonAreaMapping mapping : areaMappingRepository.findByActiveTrue()) {
            storeIdByRobotId.put(mapping.getRobotId(), mapping.getKeenonStoreId());
        }

        for (Map.Entry<UUID, String> entry : storeIdByRobotId.entrySet()) {
            try {
                syncOne(entry.getKey(), entry.getValue());
            } catch (Exception ex) {
                // Defensive: one robot's unexpected failure must never abort the batch.
                log.warn("Keenon cleaning-history sync: unexpected failure syncing robot {}: {}", entry.getKey(), ex.getMessage());
            }
        }
    }

    private void syncOne(UUID robotId, String storeId) {
        Robot robot = robotRepository.findById(robotId).orElse(null);
        if (robot == null || robot.getStatus() == RobotLifecycleStatus.DEACTIVATED) {
            return;
        }
        RobotModel model = robotModelRepository.findById(robot.getRobotModelId()).orElse(null);
        if (model == null || model.getAdapterType() != AdapterType.KEENON_CLOUD) {
            return;
        }
        if (!robotCapabilityService.isSupported(robot.getRobotModelId(), RobotCapabilityType.CLEANING)) {
            return;
        }
        try {
            int newRecords = keenonCleaningHistorySyncService.sync(robot, storeId);
            if (newRecords > 0) {
                log.info("Keenon cleaning-history sync: recorded {} new history row(s) for robot {}", newRecords, robotId);
            }
        } catch (ApiException ex) {
            log.warn("Keenon cleaning-history sync: sync failed for robot {}: {}", robotId, ex.getMessage());
        }
    }
}
