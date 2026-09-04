package com.sakarrobotics.cloud.integration.keenon;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.robot.registry.AdapterType;
import com.sakarrobotics.cloud.robot.registry.Robot;
import com.sakarrobotics.cloud.robot.registry.RobotCapabilityService;
import com.sakarrobotics.cloud.robot.registry.RobotCapabilityType;
import com.sakarrobotics.cloud.robot.registry.RobotLifecycleStatus;
import com.sakarrobotics.cloud.robot.registry.RobotModel;
import com.sakarrobotics.cloud.robot.registry.RobotModelRepository;
import com.sakarrobotics.cloud.robot.registry.RobotRepository;

/**
 * Verifies {@link KeenonMapPointSyncScheduler} is gated on {@code
 * GET_STATUS} (not {@code GET_MAP}, which C40 S does not currently grant —
 * see the class Javadoc), only syncs KEENON_CLOUD robots, skips deactivated
 * robots, and never lets one robot's failure abort the rest of the batch.
 */
@ExtendWith(MockitoExtension.class)
class KeenonMapPointSyncSchedulerTest {

    @Mock
    private RobotRepository robotRepository;
    @Mock
    private RobotModelRepository robotModelRepository;
    @Mock
    private RobotCapabilityService robotCapabilityService;
    @Mock
    private KeenonMapPointSyncService keenonMapPointSyncService;

    private KeenonMapPointSyncScheduler scheduler() {
        return new KeenonMapPointSyncScheduler(robotRepository, robotModelRepository, robotCapabilityService,
                keenonMapPointSyncService);
    }

    private Robot aRobot(UUID modelId) {
        Robot robot = new Robot();
        robot.setId(UUID.randomUUID());
        robot.setRobotModelId(modelId);
        robot.setStatus(RobotLifecycleStatus.ACTIVE);
        robot.setExternalRobotId("94:BA:06:CA:99:F3");
        return robot;
    }

    private RobotModel aModel(AdapterType adapterType) {
        RobotModel model = new RobotModel();
        model.setId(UUID.randomUUID());
        model.setAdapterType(adapterType);
        return model;
    }

    @Test
    void syncAll_syncsEveryEligibleKeenonCloudRobot_gatedOnGetStatus_notGetMap() {
        RobotModel model = aModel(AdapterType.KEENON_CLOUD);
        Robot robot = aRobot(model.getId());

        when(robotRepository.findAll()).thenReturn(List.of(robot));
        when(robotModelRepository.findById(model.getId())).thenReturn(Optional.of(model));
        when(robotCapabilityService.isSupported(model.getId(), RobotCapabilityType.GET_STATUS)).thenReturn(true);

        scheduler().syncAll();

        verify(keenonMapPointSyncService).sync(robot);
        verify(robotCapabilityService, org.mockito.Mockito.never()).isSupported(model.getId(), RobotCapabilityType.GET_MAP);
    }

    @Test
    void syncAll_skipsDeactivatedRobots() {
        RobotModel model = aModel(AdapterType.KEENON_CLOUD);
        Robot robot = aRobot(model.getId());
        robot.setStatus(RobotLifecycleStatus.DEACTIVATED);

        when(robotRepository.findAll()).thenReturn(List.of(robot));

        scheduler().syncAll();

        verifyNoInteractions(keenonMapPointSyncService);
    }

    @Test
    void syncAll_skipsNonKeenonCloudRobots() {
        RobotModel model = aModel(AdapterType.SAKAR_NATIVE);
        Robot robot = aRobot(model.getId());

        when(robotRepository.findAll()).thenReturn(List.of(robot));
        when(robotModelRepository.findById(model.getId())).thenReturn(Optional.of(model));

        scheduler().syncAll();

        verifyNoInteractions(keenonMapPointSyncService);
    }

    @Test
    void syncAll_skipsWhenGetStatusCapabilityNotSupported() {
        RobotModel model = aModel(AdapterType.KEENON_CLOUD);
        Robot robot = aRobot(model.getId());

        when(robotRepository.findAll()).thenReturn(List.of(robot));
        when(robotModelRepository.findById(model.getId())).thenReturn(Optional.of(model));
        when(robotCapabilityService.isSupported(model.getId(), RobotCapabilityType.GET_STATUS)).thenReturn(false);

        scheduler().syncAll();

        verifyNoInteractions(keenonMapPointSyncService);
    }

    @Test
    void syncAll_oneRobotsSyncFailure_doesNotAbortTheOthers() {
        RobotModel model = aModel(AdapterType.KEENON_CLOUD);
        Robot failingRobot = aRobot(model.getId());
        Robot healthyRobot = aRobot(model.getId());

        when(robotRepository.findAll()).thenReturn(List.of(failingRobot, healthyRobot));
        when(robotModelRepository.findById(model.getId())).thenReturn(Optional.of(model));
        when(robotCapabilityService.isSupported(model.getId(), RobotCapabilityType.GET_STATUS)).thenReturn(true);
        when(keenonMapPointSyncService.sync(failingRobot))
                .thenThrow(new ApiException(SakarErrorCode.VENDOR_API_ERROR, "Keenon Open Platform request failed"));

        scheduler().syncAll();

        verify(keenonMapPointSyncService).sync(failingRobot);
        verify(keenonMapPointSyncService).sync(healthyRobot);
    }

    @Test
    void syncAll_oneRobotsUnexpectedFailure_doesNotAbortTheOthers() {
        RobotModel model = aModel(AdapterType.KEENON_CLOUD);
        Robot failingRobot = aRobot(model.getId());
        Robot healthyRobot = aRobot(model.getId());

        when(robotRepository.findAll()).thenReturn(List.of(failingRobot, healthyRobot));
        when(robotModelRepository.findById(model.getId())).thenReturn(Optional.of(model));
        when(robotCapabilityService.isSupported(model.getId(), RobotCapabilityType.GET_STATUS)).thenReturn(true);
        when(keenonMapPointSyncService.sync(failingRobot)).thenThrow(new RuntimeException("unexpected"));

        scheduler().syncAll();

        verify(keenonMapPointSyncService).sync(failingRobot);
        verify(keenonMapPointSyncService).sync(healthyRobot);
    }

    @Test
    void scheduledSync_whenDisabled_neverRunsASync() throws ReflectiveOperationException {
        KeenonMapPointSyncScheduler scheduler = scheduler();
        setEnabled(scheduler, false);

        scheduler.scheduledSync();

        verifyNoInteractions(robotRepository, keenonMapPointSyncService);
    }

    @Test
    void scheduledSync_whenEnabled_runsASync() throws ReflectiveOperationException {
        KeenonMapPointSyncScheduler scheduler = scheduler();
        setEnabled(scheduler, true);
        when(robotRepository.findAll()).thenReturn(List.of());

        scheduler.scheduledSync();

        verify(robotRepository, times(1)).findAll();
    }

    private static void setEnabled(KeenonMapPointSyncScheduler scheduler, boolean value) throws ReflectiveOperationException {
        var field = KeenonMapPointSyncScheduler.class.getDeclaredField("enabled");
        field.setAccessible(true);
        field.set(scheduler, value);
    }
}
