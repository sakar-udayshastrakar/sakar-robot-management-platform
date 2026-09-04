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
 * Periodically refreshes ALREADY-established {@link KeenonAreaMapping} rows —
 * follows the exact "real, gated, wall-clock-driven sweep, one robot's
 * failure never aborts the rest" pattern as {@link KeenonStatusSyncService}.
 *
 * <p>Deliberately does not (and cannot) discover new (robot, storeId) pairs
 * on its own: {@code storeId} is not stored anywhere on {@link Robot} itself
 * — only on an already-created {@link KeenonAreaMapping} row, which only
 * ever comes from an explicit, human-supplied {@code POST
 * /robots/{id}/keenon/areas/sync} call (never hardcoded, never invented).
 * This scheduler only re-syncs (robot, storeId) pairs this system has
 * already recorded that way; a robot with no existing active mapping is
 * simply never touched here — it stays reachable only through the manual
 * endpoint until a human syncs it at least once.
 */
@Service
@RequiredArgsConstructor
public class KeenonAreaSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(KeenonAreaSyncScheduler.class);

    private final KeenonAreaMappingRepository areaMappingRepository;
    private final RobotRepository robotRepository;
    private final RobotModelRepository robotModelRepository;
    private final RobotCapabilityService robotCapabilityService;
    private final KeenonAreaSyncService keenonAreaSyncService;

    @Value("${sakar.integration.keenon.area-sync.enabled:false}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${sakar.integration.keenon.area-sync.interval-ms:21600000}")
    public void scheduledSync() {
        if (enabled) {
            syncAll();
        }
    }

    public void syncAll() {
        Map<UUID, String> storeIdByRobotId = new LinkedHashMap<>();
        for (KeenonAreaMapping mapping : areaMappingRepository.findByActiveTrue()) {
            // Every active mapping for the same robot already shares one storeId (the sync
            // service itself only ever writes one per call) — last-write-wins here is
            // harmless, not a real ambiguity.
            storeIdByRobotId.put(mapping.getRobotId(), mapping.getKeenonStoreId());
        }

        for (Map.Entry<UUID, String> entry : storeIdByRobotId.entrySet()) {
            try {
                syncOne(entry.getKey(), entry.getValue());
            } catch (Exception ex) {
                // Defensive: one robot's unexpected failure must never abort the batch.
                log.warn("Keenon area sync: unexpected failure syncing robot {}: {}", entry.getKey(), ex.getMessage());
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
        if (!robotCapabilityService.isSupported(robot.getRobotModelId(), RobotCapabilityType.GET_AREAS)) {
            return;
        }
        try {
            keenonAreaSyncService.sync(robot, storeId);
        } catch (ApiException ex) {
            log.warn("Keenon area sync: sync failed for robot {}: {}", robotId, ex.getMessage());
        }
    }
}
