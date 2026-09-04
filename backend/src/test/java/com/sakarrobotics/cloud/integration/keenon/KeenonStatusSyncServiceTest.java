package com.sakarrobotics.cloud.integration.keenon;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
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

/**
 * Robot state synchronization foundation — verifies {@link
 * KeenonStatusSyncService} only ever marks a robot offline on an explicit
 * {@code online:false} from the adapter (never on an API/network failure),
 * only syncs KEENON_CLOUD robots, and never lets one robot's failure abort
 * the rest of the batch.
 */
@ExtendWith(MockitoExtension.class)
class KeenonStatusSyncServiceTest {

    @Mock
    private RobotRepository robotRepository;
    @Mock
    private RobotModelRepository robotModelRepository;
    @Mock
    private RobotAdapterRegistry robotAdapterRegistry;
    @Mock
    private RobotCapabilityService robotCapabilityService;
    @Mock
    private RobotStatusService robotStatusService;
    @Mock
    private RobotAdapter adapter;

    private KeenonStatusSyncService service() {
        return new KeenonStatusSyncService(robotRepository, robotModelRepository, robotAdapterRegistry,
                robotCapabilityService, robotStatusService);
    }

    private Robot aRobot(UUID modelId) {
        Robot robot = new Robot();
        robot.setId(UUID.randomUUID());
        robot.setRobotModelId(modelId);
        robot.setStatus(RobotLifecycleStatus.ACTIVE);
        return robot;
    }

    private RobotModel aModel(AdapterType adapterType) {
        RobotModel model = new RobotModel();
        model.setId(UUID.randomUUID());
        model.setAdapterType(adapterType);
        return model;
    }

    @Test
    void syncOne_onlineWithBattery_appliesMainStateAndBatteryMetrics() {
        UUID modelId = UUID.randomUUID();
        Robot robot = aRobot(modelId);
        Instant observedAt = Instant.now();
        when(robotCapabilityService.isSupported(modelId, RobotCapabilityType.GET_STATUS)).thenReturn(true);
        when(robotCapabilityService.isSupported(modelId, RobotCapabilityType.GET_BATTERY)).thenReturn(true);
        when(adapter.getStatus(robot)).thenReturn(new RobotStatusSnapshot("IDLE", null, true, observedAt, null));
        when(adapter.getBattery(robot)).thenReturn(new BatteryInfo(77, false, observedAt));

        service().syncOne(robot, adapter);

        verify(robotStatusService).applyKnownMetric(eq(robot.getId()), eq("main_state"), isNull(), eq("IDLE"), eq(observedAt));
        verify(robotStatusService).applyKnownMetric(eq(robot.getId()), eq("battery_percent"), eq(77.0), isNull(), eq(observedAt));
        verify(robotStatusService, never()).markOffline(any(), any());
    }

    @Test
    void syncOne_explicitlyOffline_marksOfflineAndSkipsBattery() {
        UUID modelId = UUID.randomUUID();
        Robot robot = aRobot(modelId);
        Instant observedAt = Instant.now();
        when(robotCapabilityService.isSupported(modelId, RobotCapabilityType.GET_STATUS)).thenReturn(true);
        when(adapter.getStatus(robot)).thenReturn(new RobotStatusSnapshot(null, null, false, observedAt, null));

        service().syncOne(robot, adapter);

        verify(robotStatusService).markOffline(robot.getId(), observedAt);
        verify(robotStatusService, never()).applyKnownMetric(any(), anyString(), any(), any(), any());
        verify(adapter, never()).getBattery(any());
    }

    @Test
    void syncOne_getStatusFails_neverMarksOfflineAndNeverAppliesAMetric() {
        UUID modelId = UUID.randomUUID();
        Robot robot = aRobot(modelId);
        when(robotCapabilityService.isSupported(modelId, RobotCapabilityType.GET_STATUS)).thenReturn(true);
        when(adapter.getStatus(robot)).thenThrow(new ApiException(SakarErrorCode.VENDOR_API_ERROR, "Keenon Open Platform request failed"));

        service().syncOne(robot, adapter);

        verify(robotStatusService, never()).markOffline(any(), any());
        verify(robotStatusService, never()).applyKnownMetric(any(), anyString(), any(), any(), any());
    }

    @Test
    void syncOne_getStatusFailsWithIntegrationUnavailable_isTreatedTheSameAsAnyOtherFailure() {
        UUID modelId = UUID.randomUUID();
        Robot robot = aRobot(modelId);
        when(robotCapabilityService.isSupported(modelId, RobotCapabilityType.GET_STATUS)).thenReturn(true);
        when(adapter.getStatus(robot)).thenThrow(new ApiException(SakarErrorCode.INTEGRATION_UNAVAILABLE, "Keenon Open Platform integration is disabled"));

        service().syncOne(robot, adapter);

        verify(robotStatusService, never()).markOffline(any(), any());
        verify(robotStatusService, never()).applyKnownMetric(any(), anyString(), any(), any(), any());
    }

    @Test
    void syncOne_getStatusCapabilityNotSupported_skipsWithoutCallingTheAdapter() {
        UUID modelId = UUID.randomUUID();
        Robot robot = aRobot(modelId);
        when(robotCapabilityService.isSupported(modelId, RobotCapabilityType.GET_STATUS)).thenReturn(false);

        service().syncOne(robot, adapter);

        verifyNoInteractions(adapter);
        verify(robotStatusService, never()).markOffline(any(), any());
        verify(robotStatusService, never()).applyKnownMetric(any(), anyString(), any(), any(), any());
    }

    @Test
    void syncOne_onlineButBatteryCapabilityNotSupported_appliesOnlyMainState() {
        UUID modelId = UUID.randomUUID();
        Robot robot = aRobot(modelId);
        Instant observedAt = Instant.now();
        when(robotCapabilityService.isSupported(modelId, RobotCapabilityType.GET_STATUS)).thenReturn(true);
        when(robotCapabilityService.isSupported(modelId, RobotCapabilityType.GET_BATTERY)).thenReturn(false);
        when(adapter.getStatus(robot)).thenReturn(new RobotStatusSnapshot("IDLE", null, true, observedAt, null));

        service().syncOne(robot, adapter);

        verify(robotStatusService).applyKnownMetric(eq(robot.getId()), eq("main_state"), isNull(), eq("IDLE"), eq(observedAt));
        verify(adapter, never()).getBattery(any());
    }

    @Test
    void syncOne_onlineButBatteryCallFails_stillAppliedMainStateAndDoesNotPropagate() {
        UUID modelId = UUID.randomUUID();
        Robot robot = aRobot(modelId);
        Instant observedAt = Instant.now();
        when(robotCapabilityService.isSupported(modelId, RobotCapabilityType.GET_STATUS)).thenReturn(true);
        when(robotCapabilityService.isSupported(modelId, RobotCapabilityType.GET_BATTERY)).thenReturn(true);
        when(adapter.getStatus(robot)).thenReturn(new RobotStatusSnapshot("IDLE", null, true, observedAt, null));
        when(adapter.getBattery(robot)).thenThrow(new ApiException(SakarErrorCode.VENDOR_API_ERROR, "Keenon Open Platform request failed"));

        service().syncOne(robot, adapter);

        verify(robotStatusService).applyKnownMetric(eq(robot.getId()), eq("main_state"), isNull(), eq("IDLE"), eq(observedAt));
        verify(robotStatusService, never()).applyKnownMetric(any(), eq("battery_percent"), anyDouble(), any(), any());
    }

    @Test
    void syncAll_onlySyncsKeenonCloudRobots_andSkipsDeactivatedRobots() {
        RobotModel keenonModel = aModel(AdapterType.KEENON_CLOUD);
        RobotModel nativeModel = aModel(AdapterType.SAKAR_NATIVE);

        Robot keenonRobot = aRobot(keenonModel.getId());
        Robot nativeRobot = aRobot(nativeModel.getId());
        Robot deactivatedKeenonRobot = aRobot(keenonModel.getId());
        deactivatedKeenonRobot.setStatus(RobotLifecycleStatus.DEACTIVATED);

        when(robotAdapterRegistry.resolve(AdapterType.KEENON_CLOUD)).thenReturn(adapter);
        when(robotRepository.findAll()).thenReturn(List.of(keenonRobot, nativeRobot, deactivatedKeenonRobot));
        when(robotModelRepository.findById(keenonModel.getId())).thenReturn(java.util.Optional.of(keenonModel));
        when(robotModelRepository.findById(nativeModel.getId())).thenReturn(java.util.Optional.of(nativeModel));
        when(robotCapabilityService.isSupported(keenonModel.getId(), RobotCapabilityType.GET_STATUS)).thenReturn(true);
        when(robotCapabilityService.isSupported(keenonModel.getId(), RobotCapabilityType.GET_BATTERY)).thenReturn(true);
        when(adapter.getStatus(keenonRobot)).thenReturn(new RobotStatusSnapshot("IDLE", null, true, Instant.now(), null));
        when(adapter.getBattery(keenonRobot)).thenReturn(new BatteryInfo(50, false, Instant.now()));

        service().syncAll();

        verify(adapter, times(1)).getStatus(any());
        verify(adapter).getStatus(keenonRobot);
        verify(adapter, never()).getStatus(nativeRobot);
        verify(adapter, never()).getStatus(deactivatedKeenonRobot);
    }

    @Test
    void syncAll_oneRobotsUnexpectedFailure_doesNotStopTheRestOfTheBatch() {
        RobotModel model = aModel(AdapterType.KEENON_CLOUD);
        Robot failingRobot = aRobot(model.getId());
        Robot healthyRobot = aRobot(model.getId());

        when(robotAdapterRegistry.resolve(AdapterType.KEENON_CLOUD)).thenReturn(adapter);
        when(robotRepository.findAll()).thenReturn(List.of(failingRobot, healthyRobot));
        when(robotModelRepository.findById(model.getId())).thenReturn(java.util.Optional.of(model));
        // Simulate an unexpected (non-ApiException) failure only for the first robot's capability check.
        when(robotCapabilityService.isSupported(model.getId(), RobotCapabilityType.GET_STATUS))
                .thenThrow(new RuntimeException("unexpected"))
                .thenReturn(true);
        when(robotCapabilityService.isSupported(model.getId(), RobotCapabilityType.GET_BATTERY)).thenReturn(false);
        when(adapter.getStatus(healthyRobot)).thenReturn(new RobotStatusSnapshot("IDLE", null, true, Instant.now(), null));

        service().syncAll();

        verify(adapter, never()).getStatus(failingRobot);
        verify(adapter).getStatus(healthyRobot);
    }

    @Test
    void syncAll_noAdapterRegisteredForKeenonCloud_skipsTheWholeRunWithoutThrowing() {
        when(robotAdapterRegistry.resolve(AdapterType.KEENON_CLOUD))
                .thenThrow(new ApiException(SakarErrorCode.INTEGRATION_UNAVAILABLE, "No adapter implementation is registered"));

        service().syncAll();

        verifyNoInteractions(robotRepository);
    }

    @Test
    void scheduledSync_whenDisabled_neverRunsASync() throws ReflectiveOperationException {
        KeenonStatusSyncService service = service();
        setEnabled(service, false);

        service.scheduledSync();

        verifyNoInteractions(robotAdapterRegistry, robotRepository);
    }

    @Test
    void scheduledSync_whenEnabled_runsASync() throws ReflectiveOperationException {
        KeenonStatusSyncService service = service();
        setEnabled(service, true);
        when(robotAdapterRegistry.resolve(AdapterType.KEENON_CLOUD)).thenReturn(adapter);
        when(robotRepository.findAll()).thenReturn(List.of());

        service.scheduledSync();

        verify(robotAdapterRegistry).resolve(AdapterType.KEENON_CLOUD);
    }

    private static void setEnabled(KeenonStatusSyncService service, boolean value) throws ReflectiveOperationException {
        var field = KeenonStatusSyncService.class.getDeclaredField("enabled");
        field.setAccessible(true);
        field.set(service, value);
    }
}
