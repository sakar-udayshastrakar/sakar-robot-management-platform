package com.sakarrobotics.cloud.telemetry;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotTelemetryRepository extends JpaRepository<RobotTelemetry, Long> {

    Page<RobotTelemetry> findByRobotIdOrderByRecordedAtDesc(UUID robotId, Pageable pageable);
}
