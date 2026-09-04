package com.sakarrobotics.cloud.integration.keenon;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.robot.adapter.RobotAdapter;
import com.sakarrobotics.cloud.robot.adapter.RobotAdapterRegistry;
import com.sakarrobotics.cloud.robot.adapter.dto.BatteryInfo;
import com.sakarrobotics.cloud.robot.adapter.dto.RobotStatusSnapshot;
import com.sakarrobotics.cloud.robot.registry.AdapterType;
import com.sakarrobotics.cloud.robot.registry.Robot;
import com.sakarrobotics.cloud.robot.registry.RobotCapabilityService;
import com.sakarrobotics.cloud.robot.registry.RobotCapabilityType;
import com.sakarrobotics.cloud.robot.registry.RobotLifecycleStatus;
import com.sakarrobotics.cloud.robot.registry.RobotModel;
import com.sakarrobotics.cloud.robot.registry.RobotModelRepository;
import com.sakarrobotics.cloud.robot.registry.RobotRepository;
import com.sakarrobotics.cloud.telemetry.RobotStatusService;

import lombok.RequiredArgsConstructor;

/**
 * Robot state synchronization foundation (Keenon integration audit,
 * "Robot state synchronization foundation" slice). Polls the already-real,
 * already-tested {@link RobotAdapter#getStatus} / {@link
 * RobotAdapter#getBattery} calls for every KEENON_CLOUD-adapter robot and
 * feeds the result through the same {@link RobotStatusService} the MQTT
 * heartbeat/telemetry path already uses for agent-based robots — closing
 * the gap where a Keenon-only robot never gets a {@code robot_status} row
 * at all (no adapter/API/entity/schema change was needed for this).
 *
 * <p>Follows the same "real, gated, wall-clock-driven sweep" pattern as
 * {@link com.sakarrobotics.cloud.alert.RobotOfflineWatcherService} and
 * {@link com.sakarrobotics.cloud.command.CommandExpiryService}, but
 * defaults to <strong>disabled</strong> — unlike those two — because this
 * one makes real outbound Keenon HTTP calls and must stay opt-in until a
 * deployment has real Keenon credentials configured.
 *
 * <p>A vendor/network failure (timeout, {@code VENDOR_API_ERROR}, disabled
 * integration, unsupported capability) is never treated as evidence a
 * robot is offline — only an explicit {@code online:false} in Keenon's own
 * status response marks a robot offline here. One robot's failure never
 * aborts the rest of the batch.
 */
@Service
@RequiredArgsConstructor
public class KeenonStatusSyncService {

    private static final Logger log = LoggerFactory.getLogger(KeenonStatusSyncService.class);

    private final RobotRepository robotRepository;
    private final RobotModelRepository robotModelRepository;
    private final RobotAdapterRegistry robotAdapterRegistry;
    private final RobotCapabilityService robotCapabilityService;
    private final RobotStatusService robotStatusService;

    @Value("${sakar.integration.keenon.status-sync.enabled:false}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${sakar.integration.keenon.status-sync.interval-ms:120000}")
    public void scheduledSync() {
        if (enabled) {
            syncAll();
        }
    }

    public void syncAll() {
        RobotAdapter adapter;
        try {
            adapter = robotAdapterRegistry.resolve(AdapterType.KEENON_CLOUD);
        } catch (ApiException ex) {
            log.warn("Keenon status sync: no adapter registered for KEENON_CLOUD, skipping this run: {}", ex.getMessage());
            return;
        }

        List<Robot> robots = robotRepository.findAll();
        for (Robot robot : robots) {
            if (robot.getStatus() == RobotLifecycleStatus.DEACTIVATED) {
                continue;
            }
            RobotModel model = robotModelRepository.findById(robot.getRobotModelId()).orElse(null);
            if (model == null || model.getAdapterType() != AdapterType.KEENON_CLOUD) {
                continue;
            }
            try {
                syncOne(robot, adapter);
            } catch (Exception ex) {
                // Defensive: one robot's unexpected failure must never abort the batch.
                log.warn("Keenon status sync: unexpected failure syncing robot {}: {}", robot.getId(), ex.getMessage());
            }
        }
    }

    void syncOne(Robot robot, RobotAdapter adapter) {
        UUID robotId = robot.getId();

        if (!robotCapabilityService.isSupported(robot.getRobotModelId(), RobotCapabilityType.GET_STATUS)) {
            return;
        }

        RobotStatusSnapshot snapshot;
        try {
            snapshot = adapter.getStatus(robot);
        } catch (ApiException ex) {
            log.warn("Keenon status sync: getStatus failed for robot {}: {}", robotId, ex.getMessage());
            return;
        }

        if (!snapshot.online()) {
            // The only case in which this service marks a robot offline — an explicit,
            // real signal from Keenon itself, never inferred from a failed API call.
            robotStatusService.markOffline(robotId, snapshot.observedAt());
            return;
        }

        robotStatusService.applyKnownMetric(robotId, "main_state", null, snapshot.mainState(), snapshot.observedAt());

        if (!robotCapabilityService.isSupported(robot.getRobotModelId(), RobotCapabilityType.GET_BATTERY)) {
            return;
        }
        try {
            BatteryInfo battery = adapter.getBattery(robot);
            robotStatusService.applyKnownMetric(robotId, "battery_percent", (double) battery.percentage(), null, battery.observedAt());
        } catch (ApiException ex) {
            log.warn("Keenon status sync: getBattery failed for robot {}: {}", robotId, ex.getMessage());
        }
    }
}
