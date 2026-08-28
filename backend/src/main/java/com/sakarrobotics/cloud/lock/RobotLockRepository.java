package com.sakarrobotics.cloud.lock;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotLockRepository extends JpaRepository<RobotLock, UUID> {
}
