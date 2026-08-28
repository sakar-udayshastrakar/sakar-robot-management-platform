package com.sakarrobotics.cloud.robot.registry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotRepository extends JpaRepository<Robot, UUID> {

    boolean existsBySerialNumber(String serialNumber);

    Optional<Robot> findByExternalRobotId(String externalRobotId);

    /** Tenant-scoped listing: pass the caller's own org id plus every accessible descendant id. */
    Page<Robot> findByOrganizationIdIn(List<UUID> organizationIds, Pageable pageable);
}
