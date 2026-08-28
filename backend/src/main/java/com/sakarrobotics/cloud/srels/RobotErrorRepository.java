package com.sakarrobotics.cloud.srels;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotErrorRepository extends JpaRepository<RobotError, UUID> {
}
