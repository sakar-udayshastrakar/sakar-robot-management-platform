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
 * Periodically refreshes {@code map_points} for every KEENON_CLOUD robot —
 * follows the exact "real, gated, wall-clock-driven sweep, one robot's
 * failure never aborts the rest" pattern as every other Keenon sync
 * scheduler in this codebase. Needs no per-robot bootstrap value — {@code
 * sceneCode} is resolved internally by {@link KeenonMapPointSyncService}
 * (via {@link KeenonMapMetadataSyncService}) — so this iterates every
 * eligible robot directly.
 *
 * <p>Gated on {@link RobotCapabilityType#GET_STATUS} (the same capability
 * {@link KeenonMapMetadataSyncScheduler} uses), <strong>deliberately not
 * {@code GET_MAP}</strong> — the C40 S model does not currently grant
 * {@code GET_MAP}, and gating on it would leave this scheduler permanently
 * inert. This scheduler has its own independent {@code enabled}/{@code
 * interval-ms} configuration, disabled by default, so it can be turned on
 * without touching robot capability data at all.
 */
@Service
@RequiredArgsConstructor
public class KeenonMapPointSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(KeenonMapPointSyncScheduler.class);

    private final RobotRepository robotRepository;
    private final RobotModelRepository robotModelRepository;
    private final RobotCapabilityService robotCapabilityService;
    private final KeenonMapPointSyncService keenonMapPointSyncService;

    @Value("${sakar.integration.keenon.map-point-sync.enabled:false}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${sakar.integration.keenon.map-point-sync.interval-ms:21600000}")
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
            if (!robotCapabilityService.isSupported(robot.getRobotModelId(), RobotCapabilityType.GET_STATUS)) {
                continue;
            }
            try {
                keenonMapPointSyncService.sync(robot);
            } catch (ApiException ex) {
                log.warn("Keenon map-point sync: sync failed for robot {}: {}", robot.getId(), ex.getMessage());
            } catch (Exception ex) {
                // Defensive: one robot's unexpected failure must never abort the batch.
                log.warn("Keenon map-point sync: unexpected failure syncing robot {}: {}", robot.getId(), ex.getMessage());
            }
        }
    }
}
