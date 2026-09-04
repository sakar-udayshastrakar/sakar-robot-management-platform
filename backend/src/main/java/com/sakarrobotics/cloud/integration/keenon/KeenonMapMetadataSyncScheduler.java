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
 * Periodically refreshes {@code maps} row for every KEENON_CLOUD robot —
 * follows the exact "real, gated, wall-clock-driven sweep, one robot's
 * failure never aborts the rest" pattern as every other Keenon sync
 * scheduler in this codebase. Needs no per-robot bootstrap value (unlike
 * area/cleaning-history sync's {@code storeId}) — {@code
 * GET .../scene/v1/robot/status} only ever needs {@code robotSn} — so this
 * iterates every eligible robot directly, the same way {@link
 * KeenonStatusSyncService} does.
 *
 * <p>Gated on {@link RobotCapabilityType#GET_STATUS} — the actual,
 * evidenced vendor capability this data comes from — deliberately NOT
 * {@link RobotCapabilityType#GET_MAP}, which the C40 S model does not
 * currently grant (the capability it represents, real map-image retrieval
 * via {@link KeenonRobotAdapter#getMap}, remains unimplemented). Gating on
 * {@code GET_MAP} here would conflate two different vendor capabilities.
 */
@Service
@RequiredArgsConstructor
public class KeenonMapMetadataSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(KeenonMapMetadataSyncScheduler.class);

    private final RobotRepository robotRepository;
    private final RobotModelRepository robotModelRepository;
    private final RobotCapabilityService robotCapabilityService;
    private final KeenonMapMetadataSyncService keenonMapMetadataSyncService;

    @Value("${sakar.integration.keenon.map-metadata-sync.enabled:false}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${sakar.integration.keenon.map-metadata-sync.interval-ms:21600000}")
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
                keenonMapMetadataSyncService.sync(robot);
            } catch (ApiException ex) {
                log.warn("Keenon map-metadata sync: sync failed for robot {}: {}", robot.getId(), ex.getMessage());
            } catch (Exception ex) {
                // Defensive: one robot's unexpected failure must never abort the batch.
                log.warn("Keenon map-metadata sync: unexpected failure syncing robot {}: {}", robot.getId(), ex.getMessage());
            }
        }
    }
}
