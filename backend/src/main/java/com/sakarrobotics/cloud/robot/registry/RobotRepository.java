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

    /**
     * Scoped (not global) duplicate check for the vendor identifier — deliberately
     * per-organization rather than per-org-type, since it stays correct even if more
     * than one SAKAR_ROOT-type organization ever exists (see {@code RobotService.register}).
     */
    boolean existsByOrganizationIdAndExternalRobotId(UUID organizationId, String externalRobotId);

    /** Tenant-scoped listing: pass the caller's own org id plus every accessible descendant id. */
    Page<Robot> findByOrganizationIdIn(List<UUID> organizationIds, Pageable pageable);
}
