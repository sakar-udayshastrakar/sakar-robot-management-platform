package com.sakarrobotics.cloud.map;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotMapRepository extends JpaRepository<RobotMap, UUID> {

    /**
     * Upsert lookup key for the Keenon map-metadata sync — a robot has exactly
     * one "current map" row, updated in place as its reported scene changes,
     * never a growing history (unlike areas/cleaning-modes/back-points, which
     * can each have several current entries at once).
     */
    Optional<RobotMap> findByRobotId(UUID robotId);
}
