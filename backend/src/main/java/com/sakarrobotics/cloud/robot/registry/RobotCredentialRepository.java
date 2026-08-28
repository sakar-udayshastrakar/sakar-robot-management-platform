package com.sakarrobotics.cloud.robot.registry;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotCredentialRepository extends JpaRepository<RobotCredential, UUID> {

    Optional<RobotCredential> findByRobotId(UUID robotId);
}
