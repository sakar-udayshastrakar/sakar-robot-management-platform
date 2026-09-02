package com.sakarrobotics.cloud.srels;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotEventRepository extends JpaRepository<RobotEvent, Long> {

    Page<RobotEvent> findByRobotIdOrderByOccurredAtDesc(UUID robotId, Pageable pageable);
}
