package com.sakarrobotics.cloud.integration.keenon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sakarrobotics.cloud.robot.registry.Robot;

@ExtendWith(MockitoExtension.class)
class KeenonRobotSceneConfigServiceTest {

    @Mock
    private KeenonRobotSceneConfigRepository repository;

    private KeenonRobotSceneConfigService service() {
        return new KeenonRobotSceneConfigService(repository);
    }

    private Robot aRobot() {
        Robot robot = new Robot();
        robot.setId(UUID.randomUUID());
        return robot;
    }

    private void stubSaveEchoesArgument() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void upsert_noExistingRow_createsNewConfig() {
        Robot robot = aRobot();
        stubSaveEchoesArgument();
        when(repository.findByRobotId(robot.getId())).thenReturn(Optional.empty());

        KeenonRobotSceneConfig result = service().upsert(robot, "7ClJPR", "F");

        assertThat(result.getRobotId()).isEqualTo(robot.getId());
        assertThat(result.getSceneCode()).isEqualTo("7ClJPR");
        assertThat(result.getSceneName()).isEqualTo("F");
    }

    @Test
    void upsert_existingRow_updatesTheSameRowRatherThanCreatingADuplicate() {
        Robot robot = aRobot();
        stubSaveEchoesArgument();
        KeenonRobotSceneConfig existing = new KeenonRobotSceneConfig();
        existing.setId(UUID.randomUUID());
        existing.setRobotId(robot.getId());
        existing.setSceneCode("dTW2N7");
        existing.setSceneName("SR Cleaning");
        when(repository.findByRobotId(robot.getId())).thenReturn(Optional.of(existing));

        KeenonRobotSceneConfig result = service().upsert(robot, "7ClJPR", "F");

        assertThat(result.getId()).isEqualTo(existing.getId());
        assertThat(result.getSceneCode()).isEqualTo("7ClJPR");
        assertThat(result.getSceneName()).isEqualTo("F");
    }

    @Test
    void upsert_noSceneName_storesNullRatherThanInventingOne() {
        Robot robot = aRobot();
        stubSaveEchoesArgument();
        when(repository.findByRobotId(robot.getId())).thenReturn(Optional.empty());

        KeenonRobotSceneConfig result = service().upsert(robot, "7ClJPR", null);

        assertThat(result.getSceneName()).isNull();
    }

    @Test
    void find_noConfiguredScene_returnsEmpty() {
        UUID robotId = UUID.randomUUID();
        when(repository.findByRobotId(robotId)).thenReturn(Optional.empty());

        assertThat(service().find(robotId)).isEmpty();
    }

    @Test
    void find_configuredScene_returnsIt() {
        UUID robotId = UUID.randomUUID();
        KeenonRobotSceneConfig config = new KeenonRobotSceneConfig();
        config.setRobotId(robotId);
        config.setSceneCode("7ClJPR");
        when(repository.findByRobotId(robotId)).thenReturn(Optional.of(config));

        assertThat(service().find(robotId)).contains(config);
    }
}
