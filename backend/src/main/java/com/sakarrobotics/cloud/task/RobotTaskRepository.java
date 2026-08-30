package com.sakarrobotics.cloud.task;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotTaskRepository extends JpaRepository<RobotTask, UUID> {

    Page<RobotTask> findByRobotIdOrderByIdDesc(UUID robotId, Pageable pageable);
}
