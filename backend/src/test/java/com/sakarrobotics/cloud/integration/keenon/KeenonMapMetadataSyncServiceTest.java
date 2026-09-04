package com.sakarrobotics.cloud.integration.keenon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.map.RobotMap;
import com.sakarrobotics.cloud.map.RobotMapRepository;
import com.sakarrobotics.cloud.robot.registry.Robot;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Keenon map-metadata-sync slice. Verifies sceneCode/sceneName (the only
 * evidenced per-robot map identity fields) are parsed correctly, never
 * overwrite valid data with null, and never touch geometry (there is no
 * {@code MapPointRepository} dependency here at all — structurally
 * impossible for this service to write coordinates).
 */
@ExtendWith(MockitoExtension.class)
class KeenonMapMetadataSyncServiceTest {

    @Mock
    private KeenonApiClient client;
    @Mock
    private RobotMapRepository robotMapRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private KeenonMapMetadataSyncService service() {
        return new KeenonMapMetadataSyncService(client, robotMapRepository);
    }

    private Robot aKeenonRobot() {
        Robot robot = new Robot();
        robot.setId(UUID.randomUUID());
        robot.setExternalRobotId("94:BA:06:CA:99:F3");
        return robot;
    }

    private void stubSaveEchoesArgument() {
        when(robotMapRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void sync_newSceneCode_createsMapMetadata() throws Exception {
        Robot robot = aKeenonRobot();
        stubSaveEchoesArgument();
        when(robotMapRepository.findByRobotId(robot.getId())).thenReturn(Optional.empty());
        JsonNode response = objectMapper.readTree("{\"data\":{\"sceneCode\":\"6aJfcu\",\"sceneName\":\"Jagdish1\"}}");
        when(client.getRobotStatus("94:BA:06:CA:99:F3")).thenReturn(response);

        Optional<RobotMap> result = service().sync(robot);

        assertThat(result).isPresent();
        assertThat(result.get().getRobotId()).isEqualTo(robot.getId());
        assertThat(result.get().getVendorMapId()).isEqualTo("6aJfcu");
        assertThat(result.get().getName()).isEqualTo("Jagdish1");
        // No real URL is ever evidenced — never fabricated.
        assertThat(result.get().getImageUrl()).isNull();
    }

    @Test
    void sync_usesExternalRobotId_neverTheSakarSerialNumber() throws Exception {
        Robot robot = aKeenonRobot();
        robot.setSerialNumber("SR-CB-2026-000001");
        when(client.getRobotStatus(anyString())).thenReturn(objectMapper.readTree("{\"data\":{\"sceneCode\":\"x\"}}"));
        when(robotMapRepository.findByRobotId(robot.getId())).thenReturn(Optional.empty());
        stubSaveEchoesArgument();

        service().sync(robot);

        ArgumentCaptor<String> robotSnArg = ArgumentCaptor.forClass(String.class);
        verify(client).getRobotStatus(robotSnArg.capture());
        assertThat(robotSnArg.getValue()).isEqualTo("94:BA:06:CA:99:F3");
    }

    @Test
    void sync_repeatedSync_updatesTheSameRowRatherThanCreatingADuplicate() throws Exception {
        Robot robot = aKeenonRobot();
        stubSaveEchoesArgument();
        when(client.getRobotStatus("94:BA:06:CA:99:F3"))
                .thenReturn(objectMapper.readTree("{\"data\":{\"sceneCode\":\"6aJfcu\",\"sceneName\":\"Jagdish1\"}}"));

        when(robotMapRepository.findByRobotId(robot.getId())).thenReturn(Optional.empty());
        RobotMap first = service().sync(robot).orElseThrow();
        first.setId(UUID.randomUUID());

        when(robotMapRepository.findByRobotId(robot.getId())).thenReturn(Optional.of(first));
        RobotMap second = service().sync(robot).orElseThrow();

        assertThat(second.getId()).isEqualTo(first.getId());
    }

    @Test
    void sync_sceneChanges_updatesExistingRowFields() throws Exception {
        Robot robot = aKeenonRobot();
        stubSaveEchoesArgument();
        RobotMap existing = new RobotMap();
        existing.setId(UUID.randomUUID());
        existing.setRobotId(robot.getId());
        existing.setVendorMapId("6aJfcu");
        existing.setName("Jagdish1");
        when(robotMapRepository.findByRobotId(robot.getId())).thenReturn(Optional.of(existing));
        when(client.getRobotStatus("94:BA:06:CA:99:F3"))
                .thenReturn(objectMapper.readTree("{\"data\":{\"sceneCode\":\"8KcF4y\",\"sceneName\":\"Taj cidade goa\"}}"));

        RobotMap updated = service().sync(robot).orElseThrow();

        assertThat(updated.getId()).isEqualTo(existing.getId());
        assertThat(updated.getVendorMapId()).isEqualTo("8KcF4y");
        assertThat(updated.getName()).isEqualTo("Taj cidade goa");
    }

    @Test
    void sync_missingExternalRobotId_throwsIntegrationUnavailable_neverCallsTheVendor() {
        Robot robot = aKeenonRobot();
        robot.setExternalRobotId(null);

        assertThatThrownBy(() -> service().sync(robot))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.INTEGRATION_UNAVAILABLE));
        org.mockito.Mockito.verifyNoInteractions(client);
    }

    @Test
    void sync_vendorApiThrows_propagatesWithoutTouchingExistingMapData() {
        Robot robot = aKeenonRobot();
        when(client.getRobotStatus(anyString()))
                .thenThrow(new ApiException(SakarErrorCode.VENDOR_API_ERROR, "Keenon Open Platform request failed"));

        assertThatThrownBy(() -> service().sync(robot))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.VENDOR_API_ERROR));

        verify(robotMapRepository, never()).save(any());
    }

    @Test
    void sync_responseWithNoSceneCode_preservesExistingData_neverOverwritesWithNull() throws Exception {
        Robot robot = aKeenonRobot();
        // A response that has no "data" object at all (e.g. offline robot) — never
        // interpreted as "clear the previously-known map."
        when(client.getRobotStatus("94:BA:06:CA:99:F3")).thenReturn(objectMapper.readTree("{\"code\":\"200\"}"));

        Optional<RobotMap> result = service().sync(robot);

        assertThat(result).isEmpty();
        verify(robotMapRepository, never()).save(any());
    }

    @Test
    void sync_sceneCodePresentButNoSceneName_fallsBackToSceneCodeAsName_neverInventsAName() throws Exception {
        Robot robot = aKeenonRobot();
        stubSaveEchoesArgument();
        when(robotMapRepository.findByRobotId(robot.getId())).thenReturn(Optional.empty());
        when(client.getRobotStatus("94:BA:06:CA:99:F3")).thenReturn(objectMapper.readTree("{\"data\":{\"sceneCode\":\"6aJfcu\"}}"));

        RobotMap result = service().sync(robot).orElseThrow();

        assertThat(result.getName()).isEqualTo("6aJfcu");
    }
}
