package com.sakarrobotics.cloud.integration.keenon;

import static org.mockito.Mockito.never;
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
 * Verifies {@link KeenonCleaningHistorySyncScheduler} only refreshes robots
 * with an existing active {@link KeenonAreaMapping} (reusing its recorded
 * {@code storeId} — never inventing one), and that one robot's failure
 * never aborts the rest of the batch — mirrors {@link
 * KeenonAreaSyncSchedulerTest}'s conventions for the same reason.
 */
@ExtendWith(MockitoExtension.class)
class KeenonCleaningHistorySyncSchedulerTest {

    @Mock
    private KeenonAreaMappingRepository areaMappingRepository;
    @Mock
    private RobotRepository robotRepository;
    @Mock
    private RobotModelRepository robotModelRepository;
    @Mock
    private RobotCapabilityService robotCapabilityService;
    @Mock
    private KeenonCleaningHistorySyncService keenonCleaningHistorySyncService;

    private KeenonCleaningHistorySyncScheduler scheduler() {
        return new KeenonCleaningHistorySyncScheduler(areaMappingRepository, robotRepository, robotModelRepository,
                robotCapabilityService, keenonCleaningHistorySyncService);
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

    private KeenonAreaMapping activeMapping(UUID robotId, String storeId) {
        KeenonAreaMapping mapping = new KeenonAreaMapping();
        mapping.setRobotId(robotId);
        mapping.setKeenonStoreId(storeId);
        mapping.setActive(true);
        return mapping;
    }

    @Test
    void syncAll_refreshesEveryRobotWithAnExistingActiveAreaMapping_usingItsRecordedStoreId() {
        RobotModel model = aModel(AdapterType.KEENON_CLOUD);
        Robot robot = aRobot(model.getId());

        when(areaMappingRepository.findByActiveTrue()).thenReturn(List.of(activeMapping(robot.getId(), "C00715655")));
        when(robotRepository.findById(robot.getId())).thenReturn(Optional.of(robot));
        when(robotModelRepository.findById(model.getId())).thenReturn(Optional.of(model));
        when(robotCapabilityService.isSupported(model.getId(), RobotCapabilityType.CLEANING)).thenReturn(true);

        scheduler().syncAll();

        verify(keenonCleaningHistorySyncService).sync(robot, "C00715655");
    }

    @Test
    void syncAll_noExistingAreaMappingsAtAll_doesNothingWithoutError() {
        when(areaMappingRepository.findByActiveTrue()).thenReturn(List.of());

        scheduler().syncAll();

        verifyNoInteractions(robotRepository, keenonCleaningHistorySyncService);
    }

    @Test
    void syncAll_skipsDeactivatedRobots() {
        RobotModel model = aModel(AdapterType.KEENON_CLOUD);
        Robot robot = aRobot(model.getId());
        robot.setStatus(RobotLifecycleStatus.DEACTIVATED);

        when(areaMappingRepository.findByActiveTrue()).thenReturn(List.of(activeMapping(robot.getId(), "C00715655")));
        when(robotRepository.findById(robot.getId())).thenReturn(Optional.of(robot));

        scheduler().syncAll();

        verifyNoInteractions(keenonCleaningHistorySyncService);
    }

    @Test
    void syncAll_skipsRobotsWhoseModelIsNotKeenonCloud() {
        RobotModel model = aModel(AdapterType.SAKAR_NATIVE);
        Robot robot = aRobot(model.getId());

        when(areaMappingRepository.findByActiveTrue()).thenReturn(List.of(activeMapping(robot.getId(), "C00715655")));
        when(robotRepository.findById(robot.getId())).thenReturn(Optional.of(robot));
        when(robotModelRepository.findById(model.getId())).thenReturn(Optional.of(model));

        scheduler().syncAll();

        verifyNoInteractions(keenonCleaningHistorySyncService);
    }

    @Test
    void syncAll_skipsWhenCleaningCapabilityNotSupported() {
        RobotModel model = aModel(AdapterType.KEENON_CLOUD);
        Robot robot = aRobot(model.getId());

        when(areaMappingRepository.findByActiveTrue()).thenReturn(List.of(activeMapping(robot.getId(), "C00715655")));
        when(robotRepository.findById(robot.getId())).thenReturn(Optional.of(robot));
        when(robotModelRepository.findById(model.getId())).thenReturn(Optional.of(model));
        when(robotCapabilityService.isSupported(model.getId(), RobotCapabilityType.CLEANING)).thenReturn(false);

        scheduler().syncAll();

        verifyNoInteractions(keenonCleaningHistorySyncService);
    }

    @Test
    void syncAll_oneRobotsSyncFailure_doesNotAbortTheOthers() {
        RobotModel model = aModel(AdapterType.KEENON_CLOUD);
        Robot failingRobot = aRobot(model.getId());
        Robot healthyRobot = aRobot(model.getId());

        when(areaMappingRepository.findByActiveTrue()).thenReturn(
                List.of(activeMapping(failingRobot.getId(), "C00715655"), activeMapping(healthyRobot.getId(), "C00715655")));
        when(robotRepository.findById(failingRobot.getId())).thenReturn(Optional.of(failingRobot));
        when(robotRepository.findById(healthyRobot.getId())).thenReturn(Optional.of(healthyRobot));
        when(robotModelRepository.findById(model.getId())).thenReturn(Optional.of(model));
        when(robotCapabilityService.isSupported(model.getId(), RobotCapabilityType.CLEANING)).thenReturn(true);
        when(keenonCleaningHistorySyncService.sync(failingRobot, "C00715655"))
                .thenThrow(new ApiException(SakarErrorCode.VENDOR_API_ERROR, "Keenon Open Platform request failed"));

        scheduler().syncAll();

        verify(keenonCleaningHistorySyncService).sync(failingRobot, "C00715655");
        verify(keenonCleaningHistorySyncService).sync(healthyRobot, "C00715655");
    }

    @Test
    void syncAll_oneRobotsUnexpectedFailure_doesNotAbortTheOthers() {
        RobotModel model = aModel(AdapterType.KEENON_CLOUD);
        Robot failingRobot = aRobot(model.getId());
        Robot healthyRobot = aRobot(model.getId());

        when(areaMappingRepository.findByActiveTrue()).thenReturn(
                List.of(activeMapping(failingRobot.getId(), "C00715655"), activeMapping(healthyRobot.getId(), "C00715655")));
        when(robotRepository.findById(failingRobot.getId())).thenThrow(new RuntimeException("unexpected"));
        when(robotRepository.findById(healthyRobot.getId())).thenReturn(Optional.of(healthyRobot));
        when(robotModelRepository.findById(model.getId())).thenReturn(Optional.of(model));
        when(robotCapabilityService.isSupported(model.getId(), RobotCapabilityType.CLEANING)).thenReturn(true);

        scheduler().syncAll();

        verify(keenonCleaningHistorySyncService, never()).sync(failingRobot, "C00715655");
        verify(keenonCleaningHistorySyncService).sync(healthyRobot, "C00715655");
    }

    @Test
    void scheduledSync_whenDisabled_neverRunsASync() throws ReflectiveOperationException {
        KeenonCleaningHistorySyncScheduler scheduler = scheduler();
        setEnabled(scheduler, false);

        scheduler.scheduledSync();

        verifyNoInteractions(areaMappingRepository, robotRepository, keenonCleaningHistorySyncService);
    }

    @Test
    void scheduledSync_whenEnabled_runsASync() throws ReflectiveOperationException {
        KeenonCleaningHistorySyncScheduler scheduler = scheduler();
        setEnabled(scheduler, true);
        when(areaMappingRepository.findByActiveTrue()).thenReturn(List.of());

        scheduler.scheduledSync();

        verify(areaMappingRepository, times(1)).findByActiveTrue();
    }

    private static void setEnabled(KeenonCleaningHistorySyncScheduler scheduler, boolean value) throws ReflectiveOperationException {
        var field = KeenonCleaningHistorySyncScheduler.class.getDeclaredField("enabled");
        field.setAccessible(true);
        field.set(scheduler, value);
    }
}
