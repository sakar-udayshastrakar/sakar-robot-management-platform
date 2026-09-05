package com.sakarrobotics.cloud.integration.keenon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.map.RobotMap;
import com.sakarrobotics.cloud.map.RobotMapRepository;
import com.sakarrobotics.cloud.robot.registry.Robot;

/**
 * Keenon map-metadata-sync slice (Phase 1I). Verifies sceneCode/sceneName
 * now come exclusively from {@link KeenonRobotSceneConfig} — never from a
 * vendor call (this class has no {@link KeenonApiClient} dependency at all,
 * so "does the C40 S custom clean status response's missing sceneCode break
 * this sync" is structurally impossible), never overwrite valid data with
 * null, and never touch geometry (no {@code MapPointRepository} dependency
 * here either).
 */
@ExtendWith(MockitoExtension.class)
class KeenonMapMetadataSyncServiceTest {

    @Mock
    private KeenonRobotSceneConfigRepository sceneConfigRepository;
    @Mock
    private RobotMapRepository robotMapRepository;

    private KeenonMapMetadataSyncService service() {
        return new KeenonMapMetadataSyncService(sceneConfigRepository, robotMapRepository);
    }

    private Robot aKeenonRobot() {
        Robot robot = new Robot();
        robot.setId(UUID.randomUUID());
        robot.setExternalRobotId("94:BA:06:CA:99:F3");
        return robot;
    }

    private static KeenonRobotSceneConfig configOf(UUID robotId, String sceneCode, String sceneName) {
        KeenonRobotSceneConfig config = new KeenonRobotSceneConfig();
        config.setRobotId(robotId);
        config.setSceneCode(sceneCode);
        config.setSceneName(sceneName);
        return config;
    }

    private void stubSaveEchoesArgument() {
        when(robotMapRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void sync_configuredScene_createsMapMetadata() {
        Robot robot = aKeenonRobot();
        stubSaveEchoesArgument();
        when(robotMapRepository.findByRobotId(robot.getId())).thenReturn(Optional.empty());
        when(sceneConfigRepository.findByRobotId(robot.getId()))
                .thenReturn(Optional.of(configOf(robot.getId(), "7ClJPR", "F")));

        Optional<RobotMap> result = service().sync(robot);

        assertThat(result).isPresent();
        assertThat(result.get().getRobotId()).isEqualTo(robot.getId());
        assertThat(result.get().getVendorMapId()).isEqualTo("7ClJPR");
        assertThat(result.get().getName()).isEqualTo("F");
        // No real URL is ever evidenced — never fabricated.
        assertThat(result.get().getImageUrl()).isNull();
    }

    @Test
    void sync_noConfiguredScene_returnsEmpty_neverFallsBackToHistoricalSceneOrMapId() {
        Robot robot = aKeenonRobot();
        when(sceneConfigRepository.findByRobotId(robot.getId())).thenReturn(Optional.empty());

        Optional<RobotMap> result = service().sync(robot);

        assertThat(result).isEmpty();
        verifyNoInteractions(robotMapRepository);
    }

    @Test
    void sync_configuredSceneWithNoSceneName_fallsBackToSceneCodeAsName_neverInventsAName() {
        Robot robot = aKeenonRobot();
        stubSaveEchoesArgument();
        when(robotMapRepository.findByRobotId(robot.getId())).thenReturn(Optional.empty());
        when(sceneConfigRepository.findByRobotId(robot.getId()))
                .thenReturn(Optional.of(configOf(robot.getId(), "7ClJPR", null)));

        RobotMap result = service().sync(robot).orElseThrow();

        assertThat(result.getName()).isEqualTo("7ClJPR");
    }

    @Test
    void sync_twoRobotsWithDifferentConfiguredScenes_resolveIndependently() {
        Robot robotA = aKeenonRobot();
        Robot robotB = aKeenonRobot();
        robotB.setId(UUID.randomUUID());
        robotB.setExternalRobotId("11:22:33:44:55:66");
        stubSaveEchoesArgument();
        when(robotMapRepository.findByRobotId(robotA.getId())).thenReturn(Optional.empty());
        when(robotMapRepository.findByRobotId(robotB.getId())).thenReturn(Optional.empty());
        when(sceneConfigRepository.findByRobotId(robotA.getId()))
                .thenReturn(Optional.of(configOf(robotA.getId(), "7ClJPR", "F")));
        when(sceneConfigRepository.findByRobotId(robotB.getId()))
                .thenReturn(Optional.of(configOf(robotB.getId(), "8KcF4y", "Taj cidade goa")));

        RobotMap resultA = service().sync(robotA).orElseThrow();
        RobotMap resultB = service().sync(robotB).orElseThrow();

        assertThat(resultA.getVendorMapId()).isEqualTo("7ClJPR");
        assertThat(resultB.getVendorMapId()).isEqualTo("8KcF4y");
    }

    @Test
    void sync_robotWithNoConfiguredScene_neverUsesAnotherConfiguredRobotsScene() {
        // Lookup is strictly keyed by this robot's own id — a mock that only recognizes
        // unconfiguredRobot's id (never any other robot's, e.g. a "7ClJPR"-configured one)
        // proves the resolution can never leak across robots at the repository boundary.
        Robot unconfiguredRobot = aKeenonRobot();
        unconfiguredRobot.setId(UUID.randomUUID());
        unconfiguredRobot.setExternalRobotId("11:22:33:44:55:66");
        when(sceneConfigRepository.findByRobotId(unconfiguredRobot.getId())).thenReturn(Optional.empty());

        Optional<RobotMap> result = service().sync(unconfiguredRobot);

        assertThat(result).isEmpty();
        verifyNoInteractions(robotMapRepository);
    }

    @Test
    void sync_repeatedSync_updatesTheSameRowRatherThanCreatingADuplicate() {
        Robot robot = aKeenonRobot();
        stubSaveEchoesArgument();
        when(sceneConfigRepository.findByRobotId(robot.getId()))
                .thenReturn(Optional.of(configOf(robot.getId(), "7ClJPR", "F")));

        when(robotMapRepository.findByRobotId(robot.getId())).thenReturn(Optional.empty());
        RobotMap first = service().sync(robot).orElseThrow();
        first.setId(UUID.randomUUID());

        when(robotMapRepository.findByRobotId(robot.getId())).thenReturn(Optional.of(first));
        RobotMap second = service().sync(robot).orElseThrow();

        assertThat(second.getId()).isEqualTo(first.getId());
    }

    @Test
    void sync_configuredSceneChanges_updatesExistingRowFields() {
        Robot robot = aKeenonRobot();
        stubSaveEchoesArgument();
        RobotMap existing = new RobotMap();
        existing.setId(UUID.randomUUID());
        existing.setRobotId(robot.getId());
        existing.setVendorMapId("7ClJPR");
        existing.setName("F");
        when(robotMapRepository.findByRobotId(robot.getId())).thenReturn(Optional.of(existing));
        when(sceneConfigRepository.findByRobotId(robot.getId()))
                .thenReturn(Optional.of(configOf(robot.getId(), "8KcF4y", "Taj cidade goa")));

        RobotMap updated = service().sync(robot).orElseThrow();

        assertThat(updated.getId()).isEqualTo(existing.getId());
        assertThat(updated.getVendorMapId()).isEqualTo("8KcF4y");
        assertThat(updated.getName()).isEqualTo("Taj cidade goa");
    }

    @Test
    void sync_missingExternalRobotId_throwsIntegrationUnavailable_neverChecksSceneConfig() {
        Robot robot = aKeenonRobot();
        robot.setExternalRobotId(null);

        assertThatThrownBy(() -> service().sync(robot))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.INTEGRATION_UNAVAILABLE));
        verifyNoInteractions(sceneConfigRepository, robotMapRepository);
    }
}
