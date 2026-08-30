package com.sakarrobotics.cloud.alert;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sakarrobotics.cloud.robot.registry.Robot;
import com.sakarrobotics.cloud.robot.registry.RobotRepository;

import lombok.RequiredArgsConstructor;

/**
 * The only two alert-generation rules this backend implements (Roadmap
 * Phase 6/9): low battery (fed by real telemetry, {@link
 * com.sakarrobotics.cloud.telemetry.RobotStatusService}) and offline
 * (fed by a real scheduled sweep, {@link RobotOfflineWatcherService}).
 * Every other alert type in Master Requirements' "Alerts" list (critical
 * error, task failure, emergency, communication loss, lock/unlock) has no
 * generator yet and must not be fabricated here.
 *
 * <p>Idempotent by design: never opens a second {@code OPEN} alert of the
 * same type for the same robot while one is already open, so a robot
 * flapping between readings doesn't spam duplicate rows.
 */
@Service
@RequiredArgsConstructor
public class AlertGenerationService {

    static final int LOW_BATTERY_THRESHOLD_PERCENT = 20;
    static final int CRITICAL_BATTERY_THRESHOLD_PERCENT = 10;

    private final RobotAlertRepository robotAlertRepository;
    private final RobotRepository robotRepository;

    @Transactional
    public void evaluateBattery(UUID robotId, int batteryPercent) {
        if (batteryPercent > LOW_BATTERY_THRESHOLD_PERCENT) {
            resolveOpen(robotId, AlertType.LOW_BATTERY);
            return;
        }
        AlertSeverity severity = batteryPercent <= CRITICAL_BATTERY_THRESHOLD_PERCENT
                ? AlertSeverity.CRITICAL
                : AlertSeverity.MEDIUM;
        openIfNotAlready(robotId, AlertType.LOW_BATTERY, severity, "Battery at " + batteryPercent + "%");
    }

    @Transactional
    public void evaluateOffline(UUID robotId) {
        openIfNotAlready(robotId, AlertType.OFFLINE, AlertSeverity.HIGH,
                "No heartbeat or telemetry received within the configured offline threshold");
    }

    @Transactional
    public void resolveOffline(UUID robotId) {
        resolveOpen(robotId, AlertType.OFFLINE);
    }

    private void openIfNotAlready(UUID robotId, AlertType type, AlertSeverity severity, String message) {
        if (robotAlertRepository.existsByRobotIdAndAlertTypeAndStatus(robotId, type.name(), AlertStatus.OPEN.name())) {
            return;
        }
        Robot robot = robotRepository.findById(robotId).orElse(null);
        if (robot == null) {
            return; // defensive only — every caller already resolved robotId against a real robot
        }
        RobotAlert alert = new RobotAlert();
        alert.setRobotId(robotId);
        alert.setOrganizationId(robot.getOrganizationId());
        alert.setAlertType(type.name());
        alert.setSeverity(severity.name());
        alert.setMessage(message);
        alert.setStatus(AlertStatus.OPEN.name());
        robotAlertRepository.save(alert);
    }

    private void resolveOpen(UUID robotId, AlertType type) {
        robotAlertRepository.findFirstByRobotIdAndAlertTypeAndStatus(robotId, type.name(), AlertStatus.OPEN.name())
                .ifPresent(alert -> {
                    alert.setStatus(AlertStatus.RESOLVED.name());
                    robotAlertRepository.save(alert);
                });
    }
}
