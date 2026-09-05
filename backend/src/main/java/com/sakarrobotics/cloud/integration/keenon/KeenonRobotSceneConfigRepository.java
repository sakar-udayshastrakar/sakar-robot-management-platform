package com.sakarrobotics.cloud.integration.keenon;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KeenonRobotSceneConfigRepository extends JpaRepository<KeenonRobotSceneConfig, UUID> {

    /** One configured scene per robot — never more than one active row to resolve against. */
    Optional<KeenonRobotSceneConfig> findByRobotId(UUID robotId);
}
