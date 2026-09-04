package com.sakarrobotics.cloud.integration.keenon;

import java.util.List;

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
 * Periodically refreshes {@code keenon_back_point_mappings} for every
 * KEENON_CLOUD robot — follows the exact "real, gated, wall-clock-driven
 * sweep, one robot's failure never aborts the rest" pattern as {@link
 * KeenonCleaningModeSyncScheduler}. Like cleaning-mode sync (and unlike area
 * sync), no per-robot bootstrap value is needed — {@code
 * GET .../strategy/back/point} only ever needs {@code robotSn}, already on
 * every {@link Robot} — so this iterates every eligible robot directly.
 *
 * <p>Gated on the existing {@link RobotCapabilityType#RETURN_TO_DOCK}
 * capability rather than a new capability type — a robot's configured
 * return/charging points are exactly the data that capability's own command
 * dispatch already depends on, and the C40 S model already grants it, so no
 * new capability enum value or seed-data change was needed.
 */
@Service
@RequiredArgsConstructor
public class KeenonBackPointSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(KeenonBackPointSyncScheduler.class);

    private final RobotRepository robotRepository;
    private final RobotModelRepository robotModelRepository;
    private final RobotCapabilityService robotCapabilityService;
    private final KeenonBackPointSyncService keenonBackPointSyncService;

    @Value("${sakar.integration.keenon.back-point-sync.enabled:false}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${sakar.integration.keenon.back-point-sync.interval-ms:21600000}")
    public void scheduledSync() {
        if (enabled) {
            syncAll();
        }
    }

    public void syncAll() {
        List<Robot> robots = robotRepository.findAll();
        for (Robot robot : robots) {
            if (robot.getStatus() == RobotLifecycleStatus.DEACTIVATED) {
                continue;
            }
            RobotModel model = robotModelRepository.findById(robot.getRobotModelId()).orElse(null);
            if (model == null || model.getAdapterType() != AdapterType.KEENON_CLOUD) {
                continue;
            }
            if (!robotCapabilityService.isSupported(robot.getRobotModelId(), RobotCapabilityType.RETURN_TO_DOCK)) {
                continue;
            }
            try {
                keenonBackPointSyncService.sync(robot);
            } catch (ApiException ex) {
                log.warn("Keenon back-point sync: sync failed for robot {}: {}", robot.getId(), ex.getMessage());
            } catch (Exception ex) {
                // Defensive: one robot's unexpected failure must never abort the batch.
                log.warn("Keenon back-point sync: unexpected failure syncing robot {}: {}", robot.getId(), ex.getMessage());
            }
        }
    }
}
