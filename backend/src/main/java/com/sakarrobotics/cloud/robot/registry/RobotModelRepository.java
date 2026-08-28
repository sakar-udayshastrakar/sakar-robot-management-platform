package com.sakarrobotics.cloud.robot.registry;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotModelRepository extends JpaRepository<RobotModel, UUID> {
}
