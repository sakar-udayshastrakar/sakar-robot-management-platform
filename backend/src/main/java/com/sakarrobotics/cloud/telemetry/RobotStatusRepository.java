package com.sakarrobotics.cloud.telemetry;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotStatusRepository extends JpaRepository<RobotStatus, UUID> {
}
