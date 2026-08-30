package com.sakarrobotics.cloud.alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.sakarrobotics.cloud.IntegrationTestSupport;
import com.sakarrobotics.cloud.org.Organization;
import com.sakarrobotics.cloud.org.OrganizationType;
import com.sakarrobotics.cloud.robot.registry.AdapterType;
import com.sakarrobotics.cloud.robot.registry.IntegrationPath;
import com.sakarrobotics.cloud.robot.registry.Robot;
import com.sakarrobotics.cloud.robot.registry.RobotLifecycleStatus;
import com.sakarrobotics.cloud.robot.registry.RobotManufacturer;
import com.sakarrobotics.cloud.robot.registry.RobotManufacturerRepository;
import com.sakarrobotics.cloud.robot.registry.RobotModel;
import com.sakarrobotics.cloud.robot.registry.RobotModelRepository;
import com.sakarrobotics.cloud.robot.registry.RobotRepository;

/**
 * The two real, evidence-driven alert rules this backend implements — low
 * battery and offline (see {@link AlertGenerationService}'s Javadoc for why
 * no other alert type is fabricated here).
 */
class AlertGenerationServiceTest extends IntegrationTestSupport {

    @Autowired
    private AlertGenerationService alertGenerationService;
    @Autowired
    private RobotAlertRepository robotAlertRepository;
    @Autowired
    private RobotManufacturerRepository manufacturerRepository;
    @Autowired
    private RobotModelRepository modelRepository;
    @Autowired
    private RobotRepository robotRepository;

    @Test
    void batteryAtOrBelowThreshold_opensExactlyOneOpenLowBatteryAlert_evenAcrossRepeatedReadings() {
        Robot robot = aRobot();

        alertGenerationService.evaluateBattery(robot.getId(), 15);
        alertGenerationService.evaluateBattery(robot.getId(), 12); // still low — must not open a duplicate

        long openCount = robotAlertRepository.findAll().stream()
                .filter(a -> a.getRobotId().equals(robot.getId()))
                .filter(a -> a.getAlertType().equals(AlertType.LOW_BATTERY.name()))
                .filter(a -> a.getStatus().equals(AlertStatus.OPEN.name()))
                .count();
        assertThat(openCount).isEqualTo(1);
    }

    @Test
    void batteryAtOrBelowCriticalThreshold_opensACriticalSeverityAlert() {
        Robot robot = aRobot();

        alertGenerationService.evaluateBattery(robot.getId(), 5);

        RobotAlert alert = robotAlertRepository.findFirstByRobotIdAndAlertTypeAndStatus(
                robot.getId(), AlertType.LOW_BATTERY.name(), AlertStatus.OPEN.name()).orElseThrow();
        assertThat(alert.getSeverity()).isEqualTo(AlertSeverity.CRITICAL.name());
    }

    @Test
    void batteryRecoveringAboveThreshold_resolvesTheOpenLowBatteryAlert() {
        Robot robot = aRobot();
        alertGenerationService.evaluateBattery(robot.getId(), 15);

        alertGenerationService.evaluateBattery(robot.getId(), 80);

        boolean stillOpen = robotAlertRepository.existsByRobotIdAndAlertTypeAndStatus(
                robot.getId(), AlertType.LOW_BATTERY.name(), AlertStatus.OPEN.name());
        assertThat(stillOpen).isFalse();
    }

    @Test
    void robotReconnecting_resolvesAnOpenOfflineAlert() {
        Robot robot = aRobot();
        alertGenerationService.evaluateOffline(robot.getId());
        assertThat(robotAlertRepository.existsByRobotIdAndAlertTypeAndStatus(
                robot.getId(), AlertType.OFFLINE.name(), AlertStatus.OPEN.name())).isTrue();

        alertGenerationService.resolveOffline(robot.getId());

        assertThat(robotAlertRepository.existsByRobotIdAndAlertTypeAndStatus(
                robot.getId(), AlertType.OFFLINE.name(), AlertStatus.OPEN.name())).isFalse();
    }

    private Robot aRobot() {
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        RobotManufacturer manufacturer = manufacturerRepository.save(new RobotManufacturer("Vendor-" + UUID.randomUUID()));
        RobotModel model = new RobotModel();
        model.setManufacturerId(manufacturer.getId());
        model.setName("Model");
        model.setAdapterType(AdapterType.SAKAR_NATIVE);
        model.setIntegrationPath(IntegrationPath.SAKAR_OWNED_LOCAL);
        model = modelRepository.save(model);

        Robot robot = new Robot();
        robot.setOrganizationId(org.getId());
        robot.setRobotModelId(model.getId());
        robot.setName("Robot " + UUID.randomUUID());
        robot.setSerialNumber("SN-" + UUID.randomUUID());
        robot.setStatus(RobotLifecycleStatus.REGISTERED);
        return robotRepository.save(robot);
    }
}
