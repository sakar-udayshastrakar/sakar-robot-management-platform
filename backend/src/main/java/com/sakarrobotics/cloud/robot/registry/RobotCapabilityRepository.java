package com.sakarrobotics.cloud.robot.registry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotCapabilityRepository extends JpaRepository<RobotCapability, UUID> {

    List<RobotCapability> findByRobotModelId(UUID robotModelId);

    Optional<RobotCapability> findByRobotModelIdAndCapability(UUID robotModelId, RobotCapabilityType capability);
}
