package com.sakarrobotics.cloud.robot.registry;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotModelRepository extends JpaRepository<RobotModel, UUID> {

    /** Model-identity lookup for vendor discovery/sync — {@code robot_models} has no DB-level unique constraint on name alone, so duplicate-avoidance is this scoped lookup, not a schema constraint. */
    Optional<RobotModel> findByManufacturerIdAndName(UUID manufacturerId, String name);
}
