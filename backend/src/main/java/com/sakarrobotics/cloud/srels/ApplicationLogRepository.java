package com.sakarrobotics.cloud.srels;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationLogRepository extends JpaRepository<ApplicationLog, Long> {

    Page<ApplicationLog> findByRobotIdOrderByCreatedAtDesc(UUID robotId, Pageable pageable);
}
