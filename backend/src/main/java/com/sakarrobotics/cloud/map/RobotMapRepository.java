package com.sakarrobotics.cloud.map;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotMapRepository extends JpaRepository<RobotMap, UUID> {
}
