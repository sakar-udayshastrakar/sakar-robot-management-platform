package com.sakarrobotics.cloud.task;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotTaskRepository extends JpaRepository<RobotTask, UUID> {
}
