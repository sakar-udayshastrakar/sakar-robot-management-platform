package com.sakarrobotics.cloud.integration.keenon;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sakarrobotics.cloud.robot.registry.Robot;

import lombok.RequiredArgsConstructor;

/**
 * CRUD for {@link KeenonRobotSceneConfig} — deliberately no vendor call
 * anywhere in this class. Setting a robot's sceneCode is a pure Sakar-side
 * configuration action (see {@link KeenonMapMetadataSyncService}'s Javadoc
 * for why no live Keenon endpoint can supply one for this account).
 */
@Service
@RequiredArgsConstructor
public class KeenonRobotSceneConfigService {

    private final KeenonRobotSceneConfigRepository repository;

    @Transactional
    public KeenonRobotSceneConfig upsert(Robot robot, String sceneCode, String sceneName) {
        KeenonRobotSceneConfig config = repository.findByRobotId(robot.getId()).orElseGet(KeenonRobotSceneConfig::new);
        config.setRobotId(robot.getId());
        config.setSceneCode(sceneCode);
        config.setSceneName(sceneName);
        return repository.save(config);
    }

    public Optional<KeenonRobotSceneConfig> find(UUID robotId) {
        return repository.findByRobotId(robotId);
    }
}
