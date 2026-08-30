package com.sakarrobotics.cloud.cleaning;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CleaningSessionRepository extends JpaRepository<CleaningSession, UUID> {

    Page<CleaningSession> findByRobotIdOrderByIdDesc(UUID robotId, Pageable pageable);
}
