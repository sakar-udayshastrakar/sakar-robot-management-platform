package com.sakarrobotics.cloud.robot.registry;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotManufacturerRepository extends JpaRepository<RobotManufacturer, UUID> {

    Optional<RobotManufacturer> findByNameIgnoreCase(String name);
}
