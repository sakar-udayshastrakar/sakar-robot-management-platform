package com.sakarrobotics.cloud.telemetry;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotTelemetryRepository extends JpaRepository<RobotTelemetry, Long> {
}
