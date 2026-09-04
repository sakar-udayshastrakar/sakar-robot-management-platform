package com.sakarrobotics.cloud.integration.keenon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
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
import com.sakarrobotics.cloud.map.MapPoint;
import com.sakarrobotics.cloud.map.MapPointRepository;
import com.sakarrobotics.cloud.map.RobotMap;
import com.sakarrobotics.cloud.robot.registry.Robot;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Keenon map-point-sync slice. Verifies field parsing against only
 * evidenced fields, dynamic charging-point identification via {@code
 * type == "charge"} (never a hardcoded targetId), and that points are
 * never destroyed by a vendor failure or fabricated when no scene is
 * resolvable.
 */
@ExtendWith(MockitoExtension.class)
class KeenonMapPointSyncServiceTest {

    @Mock
    private KeenonApiClient client;
    @Mock
    private KeenonMapMetadataSyncService keenonMapMetadataSyncService;
    @Mock
    private MapPointRepository mapPointRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private KeenonMapPointSyncService service(String floorInfo) throws ReflectiveOperationException {
        KeenonMapPointSyncService service = new KeenonMapPointSyncService(client, keenonMapMetadataSyncService, mapPointRepository);
        var field = KeenonMapPointSyncService.class.getDeclaredField("defaultFloorInfo");
        field.setAccessible(true);
        field.set(service, floorInfo);
        return service;
    }

    private Robot aKeenonRobot() {
        Robot robot = new Robot();
        robot.setId(UUID.randomUUID());
        robot.setExternalRobotId("94:BA:06:CA:99:F3");
        return robot;
    }

    private RobotMap aRobotMap(String sceneCode) {
        RobotMap map = new RobotMap();
        map.setId(UUID.randomUUID());
        map.setVendorMapId(sceneCode);
        return map;
    }

    private void stubSaveEchoesArgument() {
        when(mapPointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void sync_resolvesSceneViaMapMetadataSync_thenParsesPointsUsingConfiguredFloorInfo() throws Exception {
        Robot robot = aKeenonRobot();
        RobotMap robotMap = aRobotMap("7ClJPR");
        when(keenonMapMetadataSyncService.sync(robot)).thenReturn(Optional.of(robotMap));
        when(mapPointRepository.findByMapIdAndName(robotMap.getId(), "1_Charging pile2")).thenReturn(Optional.empty());
        when(mapPointRepository.findByMapIdAndActiveTrue(robotMap.getId())).thenReturn(List.of());
        stubSaveEchoesArgument();
        JsonNode response = objectMapper.readTree("{\"data\":{\"targetList\":["
                + "{\"name\":\"1_Charging pile2\",\"type\":\"charge\",\"positionX\":255.92219426961137,\"positionY\":194.34652657507655}"
                + "]}}");
        when(client.getMapPosition("7ClJPR", "1")).thenReturn(response);

        List<MapPoint> result = service("1").sync(robot);

        assertThat(result).hasSize(1);
        MapPoint point = result.get(0);
        assertThat(point.getMapId()).isEqualTo(robotMap.getId());
        assertThat(point.getName()).isEqualTo("1_Charging pile2");
        // Dynamically identified via the vendor's own type field — never a hardcoded targetId.
        assertThat(point.getPointType()).isEqualTo("charge");
        assertThat(point.getX()).isEqualTo(255.92219426961137);
        assertThat(point.getY()).isEqualTo(194.34652657507655);
        assertThat(point.isActive()).isTrue();
    }

    @Test
    void sync_noSceneResolvable_returnsEmpty_neverCallsMapPositionOrMutatesData() throws Exception {
        Robot robot = aKeenonRobot();
        when(keenonMapMetadataSyncService.sync(robot)).thenReturn(Optional.empty());

        List<MapPoint> result = service("1").sync(robot);

        assertThat(result).isEmpty();
        verifyNoInteractions(client, mapPointRepository);
    }

    @Test
    void sync_missingExternalRobotId_throwsIntegrationUnavailable_neverCallsMetadataSyncOrVendor() throws Exception {
        Robot robot = aKeenonRobot();
        robot.setExternalRobotId(null);

        assertThatThrownBy(() -> service("1").sync(robot))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.INTEGRATION_UNAVAILABLE));
        verifyNoInteractions(keenonMapMetadataSyncService, client);
    }

    @Test
    void sync_repeatedSync_updatesTheSameRowRatherThanCreatingADuplicate() throws Exception {
        Robot robot = aKeenonRobot();
        RobotMap robotMap = aRobotMap("7ClJPR");
        when(keenonMapMetadataSyncService.sync(robot)).thenReturn(Optional.of(robotMap));
        JsonNode response = objectMapper.readTree(
                "{\"data\":{\"targetList\":[{\"name\":\"Starting point6-1\",\"type\":\"zone_start_pose\",\"positionX\":352.0,\"positionY\":212.04345703125}]}}");
        when(client.getMapPosition("7ClJPR", "1")).thenReturn(response);
        stubSaveEchoesArgument();

        when(mapPointRepository.findByMapIdAndName(robotMap.getId(), "Starting point6-1")).thenReturn(Optional.empty());
        when(mapPointRepository.findByMapIdAndActiveTrue(robotMap.getId())).thenReturn(List.of());
        UUID firstId = service("1").sync(robot).get(0).getId();

        MapPoint existing = new MapPoint();
        existing.setId(firstId);
        existing.setMapId(robotMap.getId());
        existing.setName("Starting point6-1");
        existing.setActive(true);
        when(mapPointRepository.findByMapIdAndName(robotMap.getId(), "Starting point6-1")).thenReturn(Optional.of(existing));
        when(mapPointRepository.findByMapIdAndActiveTrue(robotMap.getId())).thenReturn(List.of(existing));

        List<MapPoint> secondResult = service("1").sync(robot);

        assertThat(secondResult).hasSize(1);
        assertThat(secondResult.get(0).getId()).isEqualTo(firstId);
    }

    @Test
    void sync_pointAbsentFromLatestVendorResponse_isDeactivated() throws Exception {
        Robot robot = aKeenonRobot();
        RobotMap robotMap = aRobotMap("7ClJPR");
        when(keenonMapMetadataSyncService.sync(robot)).thenReturn(Optional.of(robotMap));
        MapPoint stale = new MapPoint();
        stale.setId(UUID.randomUUID());
        stale.setMapId(robotMap.getId());
        stale.setName("Old Point");
        stale.setActive(true);
        when(mapPointRepository.findByMapIdAndActiveTrue(robotMap.getId())).thenReturn(List.of(stale));
        when(mapPointRepository.findByMapIdAndName(robotMap.getId(), "New Point")).thenReturn(Optional.empty());
        stubSaveEchoesArgument();
        JsonNode response = objectMapper.readTree(
                "{\"data\":{\"targetList\":[{\"name\":\"New Point\",\"type\":\"normal\",\"positionX\":1.0,\"positionY\":2.0}]}}");
        when(client.getMapPosition("7ClJPR", "1")).thenReturn(response);

        service("1").sync(robot);

        verify(mapPointRepository).save(argThat(m -> "Old Point".equals(m.getName()) && !m.isActive()));
    }

    @Test
    void sync_emptySuccessfulTargetList_deactivatesEveryPreviouslyActivePoint() throws Exception {
        Robot robot = aKeenonRobot();
        RobotMap robotMap = aRobotMap("7ClJPR");
        when(keenonMapMetadataSyncService.sync(robot)).thenReturn(Optional.of(robotMap));
        MapPoint stale = new MapPoint();
        stale.setId(UUID.randomUUID());
        stale.setMapId(robotMap.getId());
        stale.setName("Old Point");
        stale.setActive(true);
        when(mapPointRepository.findByMapIdAndActiveTrue(robotMap.getId())).thenReturn(List.of(stale));
        stubSaveEchoesArgument();
        when(client.getMapPosition("7ClJPR", "1")).thenReturn(objectMapper.readTree("{\"data\":{\"targetList\":[]}}"));

        List<MapPoint> result = service("1").sync(robot);

        assertThat(result).isEmpty();
        verify(mapPointRepository).save(argThat(m -> !m.isActive()));
    }

    @Test
    void sync_vendorApiThrows_propagatesWithoutDeactivatingOrMutatingAnything() throws Exception {
        Robot robot = aKeenonRobot();
        RobotMap robotMap = aRobotMap("7ClJPR");
        when(keenonMapMetadataSyncService.sync(robot)).thenReturn(Optional.of(robotMap));
        when(client.getMapPosition(anyString(), anyString()))
                .thenThrow(new ApiException(SakarErrorCode.VENDOR_API_ERROR, "Keenon Open Platform request failed"));

        assertThatThrownBy(() -> service("1").sync(robot))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.VENDOR_API_ERROR));

        verify(mapPointRepository, never()).save(any());
    }

    @Test
    void sync_malformedEntryMissingName_isSkippedNeverFabricated() throws Exception {
        Robot robot = aKeenonRobot();
        RobotMap robotMap = aRobotMap("7ClJPR");
        when(keenonMapMetadataSyncService.sync(robot)).thenReturn(Optional.of(robotMap));
        when(mapPointRepository.findByMapIdAndActiveTrue(robotMap.getId())).thenReturn(List.of());
        JsonNode response = objectMapper.readTree("{\"data\":{\"targetList\":[{\"type\":\"charge\",\"positionX\":1.0}]}}");
        when(client.getMapPosition("7ClJPR", "1")).thenReturn(response);

        List<MapPoint> result = service("1").sync(robot);

        assertThat(result).isEmpty();
        verify(mapPointRepository, never()).save(any());
    }

    private static MapPoint argThat(java.util.function.Predicate<MapPoint> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }
}
